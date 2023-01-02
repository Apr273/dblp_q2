package org.homework.client;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;

import org.homework.server.BaseInfo;

public class LogQuery {

    static BaseInfo baseInfo = new BaseInfo();

    static ArrayList<Integer> portList = new ArrayList<>();

    public static void main(String[] args) throws UnknownHostException {
        System.out.println("进入组成员日志查询系统（输入q退出）");
        Scanner scan = new Scanner(System.in);
        String info = "";

        while (true) {
            System.out.println("");
            System.out.println("请输入要查询的关键字:");
            if (scan.hasNextLine()) {
                info = scan.nextLine();
                if (info.equals("q"))
                    return;
                query(info);
            }
        }
    }

    static class queryThread implements Runnable {
        static Integer port;
        static String word;

        queryThread(Integer port, String word) {
            queryThread.port = port;
            queryThread.word = word;
        }

        @Override
        public void run() {
            try {
                Socket socket = new Socket(baseInfo.introducerIp, port);
                DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
                DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                dataOutputStream.writeUTF(word);
                String result = dataInputStream.readUTF();
                System.out.println(result);
            } catch (IOException e) {
               System.out.println("[queryError]:无法与"+port+"通信！");
            }
        }

    }

    /**
     * 1.与introducer通信，获取现在存活的ip列表
     * 2.向各个ip发送请求
     */
    public static void query(String info)  {
        try {
            portList = getIpList();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        System.out.println("[query]:开始查询");
        System.out.println("[query]:结果如下");
        System.out.println("==========================");
        for (Integer port : portList) {
            Thread t = new Thread(new queryThread(port, info));
            t.start();
        }
    }

    /**
     * 与introducer通信获取最新的列表
     * 1.发送Ip
     * 2.创建数据报，包含发送的数据信息
     * 3.创建DatagramSocket对象
     * 4.向服务器端发送数据报
     */
    public static ArrayList<Integer> getIpList() throws UnknownHostException {
        InetAddress address;//introducer的ip地址
        address = InetAddress.getByName(baseInfo.introducerIp); //新加入节点的ip地址
        byte[] data = "client".getBytes();//发送ip

        //创建数据报，包含发送的数据信息
        DatagramPacket packet = new DatagramPacket(data, data.length, address, baseInfo.introducerPort);
        try (
             DatagramSocket socket = new DatagramSocket()//创建DatagramSocket对象
        ) {
            socket.send(packet); //  接收服务器端响应的数据

            byte[] data2 = new byte[1024];//创建数据报，用于接收服务器端响应的数据
            DatagramPacket packet2 = new DatagramPacket(data2, data2.length);
            socket.setSoTimeout(10000);//接收服务器响应的数据
            socket.receive(packet2);
            String reply = new String(data2, 0, packet2.getLength()); //读取数据
            System.out.println("[query]:从introducer获取最新的成员列表: \n" + reply);

            socket.close();//关闭资源

            if (reply.length() > 0) {//reply应为新的组成员列表的内容
                String[] newMemberList = reply.split("\n");
                portList.clear();
                for (String newmember : newMemberList) {
                    String newmember_port = newmember.split(" ")[1];
                    portList.add(Integer.parseInt(newmember_port));
                }
            }
        } catch (IOException e) {
            //出现异常
            System.out.println("[queryError]:无法与introducer通信！");
            return portList;
        }
        return portList;
    }


}