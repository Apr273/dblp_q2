package org.homework.server;

import org.homework.entity.Logs;
import org.homework.entity.MemberList;

import java.io.IOException;
import java.net.*;

/**
 * 维护组成员的线程
 */
public class Daemon {
    public boolean inGroup = false;
    public static Integer port;//服务器自身的端口
    //1.探测相邻的节点是否存活
    //2.主动离开
    //3.显示组成员
    //将三个子线程作为Daemon的属性，便于管理
    private Thread sendBeatingThread;

    public SendBeating sendBeating;

    private Thread receiveBeatingThread;

    private Thread gossipThread;

    private Thread waitIntroduceThread;

    //日志维护
    Logs logs;

    //每个daemon维护的全局状态列表
    MemberList groupMemberList = new MemberList();

    //获取introducer等常量配置
    BaseInfo baseInfo = new BaseInfo();

    //构造函数
    Daemon(Integer port) {
        Daemon.port = port;
        logs = new Logs(port);
        joinGroup();
    }

    /**
     * 模拟丢包处理
     */
    public boolean packageLost(double lossRate) {
        return !(Math.random() > lossRate);
    }

    public void send(DatagramSocket socket, DatagramPacket datagramPacket, double lossRate) throws IOException {
        if (packageLost(lossRate))
            System.out.println("[info]:产生模拟丢包!");
        else
            socket.send(datagramPacket);
    }

    /**
     * 接收客户端发送的数据
     * 1.创建服务器端DatagramSocket，指定端口
     * 2.创建数据报，用于接收客户端发送的数据
     * 3.接收客户端发送的数据
     * 4.读取数据
     */
    void sendNewMemberMessage() throws IOException {
        //创建服务器端DatagramSocket，指定端口
        while (!waitIntroduceThread.isInterrupted()) {
            DatagramSocket socket = new DatagramSocket();
            byte[] data = new byte[1024];// 创建字节数组，指定接收的数据包的大小
            DatagramPacket packet = new DatagramPacket(data, data.length); //创建数据报，用于接收客户端发送的数据
            socket.receive(packet);//接收客户端发送的数据，接收到数据报之前一直阻塞
            String info = new String(data, 0, packet.getLength());//读取数据
            System.out.println("[new member]:新节点加入：" + info);

            String timestamp = info.split(" ")[0];
            String newport = info.split(" ")[1];
            groupMemberList.memberAdd(timestamp, Integer.parseInt(newport));

            /**
             * 向客户端响应数据
             */
            InetAddress address = packet.getAddress(); //定义客户端的地址、端口号、数据
            int port2 = packet.getPort();
            byte[] data2 = (port2 + "已收到新节点消息").getBytes();
            //创建数据报，包含响应的数据信息
            DatagramPacket packet2 = new DatagramPacket(data2, data2.length, address, port2);
            socket.send(packet2);//响应客户端

            socket.close(); //关闭资源
        }

    }


    class listenToIntroducer implements Runnable {
        @Override
        public void run() {
            try {
                sendNewMemberMessage();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
    }

    /**
     * 加入组成员
     */
    public void joinGroup() {
        try {
            join();
            inGroup = true;
        } catch (IOException e) {
            System.out.println("[error]:无法与introducer通信！");
        }
        if (inGroup) {
            System.out.println(port);
            sendBeating = new SendBeating();
            sendBeatingThread = new Thread(sendBeating);//检查下一个成员
            receiveBeatingThread = new Thread(new ReceiveBeating());//被上一个成员检查
            gossipThread = new Thread(new gossipPort());
            waitIntroduceThread = new Thread(new listenToIntroducer());

            sendBeatingThread.start();
            receiveBeatingThread.start();
            waitIntroduceThread.start();
            gossipThread.start();
        }
    }

    /**
     * 与introducer通信
     */
    private void join() throws IOException {
        request(port); //向introducer发出加入请求
    }

    /**
     * 向introducer发送join请求
     */
    public void request(Integer myport) throws UnknownHostException {

        InetAddress address; //定义服务器的地址、端口号、数据
        address = InetAddress.getByName(baseInfo.introducerIp);//introducer的port地址
        byte[] data = myport.toString().getBytes();//新加入节点的port地址//发送port

        //创建数据报，包含发送的数据信息
        DatagramPacket packet = new DatagramPacket(data, data.length, address, baseInfo.introducerPort);
        while (true) {
            try (
                 DatagramSocket socket = new DatagramSocket(port)//创建DatagramSocket对象
            ) {
                send(socket, packet, baseInfo.loss);// 4.向服务器端发送数据报

                /**
                 * 接收服务器端响应的数据
                 */
                byte[] data2 = new byte[1024];//创建数据报，用于接收服务器端响应的数据
                DatagramPacket packet2 = new DatagramPacket(data2, data2.length);
                socket.setSoTimeout(10000);//接收服务器响应的数据
                socket.receive(packet2);
                String reply = new String(data2, 0, packet2.getLength());//读取数据
                System.out.println("[join]:从introducer获取到最新的成员列表: \n" + reply);

                socket.close();//关闭资源

                if (reply.length() > 0) {//reply应为新的组成员列表的内容
                    System.out.println("[join]:成功加入！"+myport);
                    logs.writeInfo("join " + myport);
                    String[] newMemberList = reply.split("\n");

                    for (String newMember : newMemberList) {
                        String newMemberTimeStamp = newMember.split(" ")[0];
                        String newMemberPort = newMember.split(" ")[1];
                        groupMemberList.memberAdd(newMemberTimeStamp, Integer.parseInt(newMemberPort));
                    }
                    return;
                }
                System.out.println("[joinError]:" + myport + "无法加入");
            } catch (IOException e) {
                //出现异常
                e.printStackTrace();
                System.out.println("[joinError]:" + myport + "无法加入");
            }
        }
    }

    /**
     * 显示组成员
     */
    public void showGroup() {
        groupMemberList.memberShow();
    }

    /**
     * 主动离开
     */
    public void leave() {
        //发送gossip协议
        String info = "gossip" + " " + port + " " + "leave" + " " + port;
        logs.writeInfo(info);
        InetAddress address;
        DatagramPacket packet;
        InetAddress introducerAddress;
        DatagramPacket introducerPacket;
        try {
            //逆向传播消息
            address = InetAddress.getByName(baseInfo.introducerIp);
            introducerAddress = InetAddress.getByName(baseInfo.introducerIp);
            byte[] data = info.getBytes();
            //创建数据报，包含发送的数据信息
            packet = new DatagramPacket(data, data.length, address, baseInfo.gossipPort);
            introducerPacket = new DatagramPacket(data, data.length, introducerAddress, baseInfo.introducerListPort);
            DatagramSocket socket = new DatagramSocket();
            socket.send(packet);
            socket.send(introducerPacket);
        } catch (UnknownHostException e) {
            return;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        groupMemberList.memberRemove(port);
        sendBeatingThread.interrupt();
        receiveBeatingThread.interrupt();
        gossipThread.interrupt();
        inGroup = false;
        System.out.println("[leave]:" + port + "已主动离开");

    }

    public void findLeave(Integer leaveport) {
        //要删除的点就是原来自己探测的点
        assert leaveport.equals(groupMemberList.findNextServer(port));
        System.out.println("[failure]:检测到" + leaveport + "故障");
        //更新本地逻辑数组
        groupMemberList.memberRemove(port);
        //更新自己要检查的点
        sendBeating.portToCheck = groupMemberList.findNextServer(port);
        // sendBeating.portToCheck = sendBeating.serverToCheck.port;
        sendBeating.errorNum = 0;
        if (port.equals(sendBeating.portToCheck)) {
            System.out.println("[warning]:检测到组中现在只有一个成员！");
            // return;
        }
        //发送gossip协议
        String info = "gossip" + " " + port + " " + "failure" + " " + leaveport;
        logs.writeInfo(info);

        InetAddress address;
        DatagramPacket packet;
        DatagramPacket introducerPacket;
        try {
            InetAddress introducerAddress = InetAddress.getByName(baseInfo.introducerIp);
            address = InetAddress.getByName(baseInfo.introducerIp);
            byte[] data = info.getBytes();
            // 2.创建数据报，包含发送的数据信息
            packet = new DatagramPacket(data, data.length, address, baseInfo.gossipPort);
            introducerPacket = new DatagramPacket(data, data.length, introducerAddress, baseInfo.introducerListPort);
            DatagramSocket socket = new DatagramSocket();
            //socket.send(packet);
            send(socket, packet, baseInfo.loss);
            send(socket, introducerPacket, baseInfo.loss);
        } catch (UnknownHostException e) {
            return;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 接受gossip包，并将其转发
     */
    class gossipPort implements Runnable {

        @Override
        public void run() {
            try (DatagramSocket socket = new DatagramSocket()) {
                while (!gossipThread.isInterrupted()) {
                    // 2.创建数据报，用于接收客户端发送的数据
                    byte[] data = new byte[1024];// 创建字节数组，指定接收的数据包的大小
                    DatagramPacket packet = new DatagramPacket(data, data.length);
                    // 3.接收客户端发送的数据
                    // System.out.println("准备接收gossport...");
                    socket.receive(packet);// 此方法在接收到数据报之前会一直阻塞
                    // 4.读取数据
                    String info = new String(data, 0, packet.getLength());
                    // System.out.println("收到gossport: "+ info);
                    logs.writeInfo(info);
                    String[] words = info.trim().split("\\s+");


                    if (words.length != 4 || !words[0].equals("gossip"))
                        continue;

                    //下一跳的地址
                    Integer nextport = 0;
                    int word3 = Integer.parseInt(words[3]);
                    int word1 = Integer.parseInt(words[1]);
                    //如果是探测的故障，则直接更新列表即可，其他结点不需改变探测拓扑
                    if (words[2].equals("failure")) {
                        //这里做了特判处理，不需要改变探测拓扑了（不过这样写可拓展性不高）
                        System.out.println(word3);
                        groupMemberList.memberRemove(word3);
                        nextport = groupMemberList.findNextServer(port);
                        //打印日志（）
                    }

                    if (words[2].equals("leave")) {
                        //先找到原本的lastServer再改变memberList!否则会无法结束传播！
                        System.out.println(word3 + " leave");
                        nextport = groupMemberList.findLastServer(port);
                        //判断要不要改变探测拓扑
                        if (sendBeating.portToCheck.equals(word3)) {
                            groupMemberList.memberRemove(word3);
                            sendBeating.portToCheck = groupMemberList.findNextServer(port);
                            sendBeating.errorNum = 0;
                        } else
                            groupMemberList.memberRemove(word3);

                    }

                    // 转发gossip
                    if (!nextport.equals(word1)) {
                        InetAddress address = InetAddress.getByName(baseInfo.introducerIp);
                        // 2.创建数据报，包含响应的数据信息
                        DatagramPacket packet2 = new DatagramPacket(data, data.length, address, baseInfo.gossipPort);
                        // 3.响应客户端
                        //socket.send(packet2);
                        send(socket, packet2, baseInfo.loss);
                    }
                }
            } catch (IOException e) {
                System.out.println("[error]:gossip接收错误！");
                e.printStackTrace();
            }

        }
    }

    //heart-beating检测，daemon的内部类
    class SendBeating implements Runnable {

        Integer portToCheck = groupMemberList.findNextServer(port);

        //发送检测信息的间隔时间 单位:毫秒
        int interval = 5000;

        //累积的错误次数，超过容忍度就认为结点故障了
        int errorNum = 0;

        @Override
        public void run() {
            /*
             * 每隔0.5s发送数据
             * todo:优化while循环的位置
             *
             */
            //用udp访问，检测portToCheck的状态

            while (!sendBeatingThread.isInterrupted()) {
                //check成员列表中排在自己之后的节点状态
                //休眠间隔
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    // throw new RuntimeException(e);
                    return;
                }

                portToCheck = groupMemberList.findNextServer(port);

                //System.out.println("向"+portToCheck+"发送heartbeating.");
                if (errorNum >= baseInfo.tolerate) {
                    //重置errorNum
                    errorNum = 0;
                    findLeave(portToCheck);
                }
                //System.out.println(port + "试图与" + portToCheck + "建立udp通信");
                // 1.定义服务器的地址、端口号、数据
                InetAddress address;
                DatagramPacket packet; //= new DatagramPacket(null, portToCheck);
                try {
                    address = InetAddress.getByName(baseInfo.introducerIp);
                    byte[] data = (portToCheck + "你还在吗？").getBytes();
                    // 2.创建数据报，包含发送的数据信息
                    packet = new DatagramPacket(data, data.length, address, baseInfo.heartBeatingPort);

                } catch (UnknownHostException e) {
                    System.out.println("[error]:unKnownHost");
                    System.out.println(e);
                    errorNum++;
                    continue;
                }

                try (// 3.创建DatagramSocket对象
                     //这里是不需要加端口的（?）
                     DatagramSocket socket = new DatagramSocket(baseInfo.heartBeatingPort)) {
                    // 4.向服务器端发送数据报
                    //socket.send(packet);
                    // System.out.print(LocalTime.now());
                    // System.out.println("向"+portToCheck+"发送心跳");
                    send(socket, packet, baseInfo.loss);
                    /*
                     * 接收服务器端响应的数据
                     * 创建计时器
                     */
                    // 1.创建数据报，用于接收服务器端响应的数据
                    byte[] data2 = new byte[1024];
                    DatagramPacket packet2 = new DatagramPacket(data2, data2.length);
                    // 2.接收服务器响应的数据
                    //若超时未收到则报错
                    socket.setSoTimeout(5000);
                    socket.receive(packet2);
                    // 3.读取数据
                    String reply = new String(data2, 0, packet2.getLength());
                    // System.out.println(portToCheck+"回复: " + reply);
                    // 4.关闭资源
                    // System.out.print(LocalTime.now());
                    // System.out.println("收到"+portToCheck+"的回复 ");
                    socket.close();

                    if (reply.equals("还在")) {
                        // System.out.println(portToCheck + "测试正常");
                        errorNum = 0;
                    }

                } catch (IOException e) {
                    System.out.println("[error]:IOException");
                    System.out.println(e);
                    errorNum++;
                }

            }
        }
    }

    //与sendBeating相对的，接受来自其他结点的beating
    class ReceiveBeating implements Runnable {
        //check成员列表中排在自己之后的节点状态

        @Override
        public void run() {
            //用udp访问，检测portToCheck的状态
//            List<Integer> ids = new ArrayList<>();
//            Collections.sort(ids);
//            List<Integer> nextPort = findNextPort(ids, 3, 50000, 60000);
//            int port = nextPort.stream().findFirst().orElseThrow(() -> new RuntimeException("没有多余的端口"));
            try (/*
             * 接收其他server发送的数据
             */
                    // 1.创建服务器端DatagramSocket，指定端口
                    DatagramSocket socket = new DatagramSocket(port)) {

                while (!receiveBeatingThread.isInterrupted()) {
                    // 2.创建数据报，用于接收客户端发送的数据
                    byte[] data = new byte[1024];// 创建字节数组，指定接收的数据包的大小
                    DatagramPacket packet = new DatagramPacket(data, data.length);
                    // 3.接收客户端发送的数据
                    // System.out.println(port + "等待其他成员发送heartbeating");
                    socket.receive(packet);// 此方法在接收到数据报之前会一直阻塞
                    // 4.读取数据
                    String info = new String(data, 0, packet.getLength());
                    // System.out.println("有人发来消息：" + info);

                    /*
                     * 向客户端响应数据
                     */
                    // 1.定义客户端的地址、端口号、数据
                    InetAddress address = packet.getAddress();
                    int port2 = packet.getPort();
                    byte[] data2 = "还在".getBytes();
                    // 2.创建数据报，包含响应的数据信息
                    DatagramPacket packet2 = new DatagramPacket(data2, data2.length, address, port2);
                    // 3.响应客户端
                    //socket.send(packet2);
                    // System.out.print(LocalTime.now());
                    // System.out.println("向"+address+"发送回复 ");
                    send(socket, packet2, baseInfo.loss);
                    // System.out.println("回复： 还在");
                    // 4.关闭资源
                    // socket在try的（）中声明，退出try后会自动close。
                    // socket.close();
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                System.out.println("[error]:ReceiveBeating接收错误！");
                e.printStackTrace();
            }

        }

    }


}
