package org.homework.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.homework.common.ServerInfo;
import org.homework.entity.GroupMemberList;
import org.homework.entity.GroupMemberList.MemberInfo;

/**
 * introducer引荐人
 */
public class Introducer {
    public static String ip = "127.0.0.1";
    static ServerInfo serverInfo = new ServerInfo();

    //introducer节点也需要维护一个全局的表，且保证其是最新的
    static GroupMemberList groupMemberList = new GroupMemberList();

    /**
     * 等待新节点，加入新节点
     */
    public static void addMember(Integer ipToAdd) {
        long timestamp = System.currentTimeMillis();
        groupMemberList.memberAdd(String.valueOf(timestamp), ipToAdd);
        spreadNewMember(String.valueOf(timestamp));
        System.out.println("[introducer]:" + ipToAdd + "加入组！");

    }

    static class RunnableBroadCast implements Runnable {
        String timeStamp;
        String ip;
        Integer destport;
        Thread t;

        RunnableBroadCast(String timeStamp, String ip, Integer destport) {
            this.destport = destport;
            this.ip = ip;
            this.timeStamp = timeStamp;
        }

        public void start() {
            if (t == null) {
                t = new Thread(this);
                t.start();
            }
        }

        /**
         * 向members发送新成员join消息
         * 1.发送Ip
         * 2.创建数据报，包含发送的数据信息
         * 3.创建DatagramSocket对象
         * 4.向服务器端发送数据报
         */
        public void sendMessage() throws UnknownHostException {
            InetAddress address = InetAddress.getByName(serverInfo.introducerIp);//新节点ip地址
            byte[] data = (timeStamp + " " + ip).getBytes();//发送新节点ip
            //创建数据报，包含发送的数据信息
            DatagramPacket packet = new DatagramPacket(data, data.length, address, serverInfo.broadcastPort);

            //创建DatagramSocket对象
            try (
                 DatagramSocket socket = new DatagramSocket()) {
                                socket.send(packet);//向服务器端发送数据报

                System.out.println("[introducer]:成功向" + destport + "发送新成员信息！");

                /**
                 * 接收服务器端响应的数据
                 */
                byte[] data2 = new byte[1024];//创建数据报，用于接收服务器端响应的数据
                DatagramPacket packet2 = new DatagramPacket(data2, data2.length);
                socket.receive(packet2);//接收服务器响应的数据
                String reply = new String(data2, 0, packet2.getLength());//读取数据
                System.out.println("[introducer]:从" + destport + "获取到reply: " + reply);

                socket.close();// 关闭资源

            } catch (IOException e) { //出现异常
                System.out.println("[introducerError]:无法向" + destport + "发送新成员加入消息！");
                return;
            }
        }

        @Override
        public void run() {
            try {
                sendMessage();
            } catch (UnknownHostException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    static class ReceiveChange implements Runnable {
        @Override
        public void run() {
            while (true) {
                //创建数据报，用于接收客户端发送的数据
                try (DatagramSocket socket = new DatagramSocket(serverInfo.introducerListPort)) {
                    byte[] data = new byte[1024];// 创建字节数组，指定接收的数据包的大小
                    DatagramPacket packet = new DatagramPacket(data, data.length);
                    socket.receive(packet);//接收客户端发送的数据// 此方法在接收到数据报之前会一直阻塞
                    String info = new String(data, 0, packet.getLength()); //读取数据
                    String[] words = info.trim().split("\\s+");//解析数据

                    if (words.length != 4 || !words[0].equals("gossip")) {
                        System.out.println("[introducerError]:消息格式无效！");
                        continue;
                    }

                    if (words[2].equals("failure") || words[2].equals("leave")) {
                        groupMemberList.memberRemove(Integer.parseInt(words[3]));
                    }
                } catch (IOException e) {
                    System.out.println("[introducerError]:监听列表变化出现故障！");
                    e.printStackTrace();
                }
            }
        }

    }

    private static void spreadNewMember(String timeStamp) {
        for (MemberInfo member : GroupMemberList.members) {
            RunnableBroadCast r = new RunnableBroadCast(timeStamp, ip, member.port);
            r.start();
        }

    }


    public static void main(String[] args) {
        Thread listenListThread = new Thread(new ReceiveChange());
        listenListThread.start();
        while (true) {
            //introducer执行该程序
            try (/*
             * 接收其他server发送的数据
             */
                // 1.创建服务器端DatagramSocket，指定端口
                DatagramSocket socket = new DatagramSocket(serverInfo.introducerPort)) {
                // 2.创建数据报，用于接收客户端发送的数据
                byte[] data = new byte[1024];// 创建字节数组，指定接收的数据包的大小
                DatagramPacket packet = new DatagramPacket(data, data.length);
                // 3.接收客户端发送的数据
                System.out.println("[introducer]: introducer(" + ip + ")已经启动，等待对方发送数据");
                socket.receive(packet);// 此方法在接收到数据报之前会一直阻塞
                // 4.读取数据
                String info = new String(data, 0, packet.getLength());
                System.out.println("[introducer]:对方发来消息：" + info);

                /*
                 * 向客户端响应数据
                 */
                // 1.定义客户端的地址、端口号、数据
                InetAddress address = packet.getAddress();
                int port = packet.getPort();

                //将新加入的节点更新至本地列表（关于端口的管理待定，端口可能是不需要维护在全局表中的）
                if (!info.equals("client")) {
                    port = Integer.parseInt(info);
                    addMember(port);
                }

                byte[] data2 = groupMemberList.members_toString().getBytes();
                // 2.创建数据报，包含响应的数据信息
                DatagramPacket packet2 = new DatagramPacket(data2, data2.length, address, port);
                // 3.响应客户端
                // 4.向全局更新memberlist
                socket.send(packet2);

                // 5.关闭资源
                socket.close();

            } catch (IOException e) {
                // TODO Auto-generated catch block
                System.out.println("[error]:接收错误！");
                e.printStackTrace();
            }
        }
    }

}

