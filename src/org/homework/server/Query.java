package org.homework.server;

import org.homework.entity.Logs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Query implements Runnable {

    Integer port;

    public Query(Integer port) {
        this.port = port;
    }

    public void run() {
        try {
            System.out.println("[query]:启动query服务");


            Logs logs = new Logs(port);
            ServerSocket serverSocket = new ServerSocket(port);
            //1.建立连接等待客户端socket
            while (true) {
                Socket socket = serverSocket.accept();
                DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
                DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                String info = dataInputStream.readUTF();
                String result = logs.query(info);
                dataOutputStream.writeUTF(result);
            }
        } catch (IOException e) {
            System.out.println("[error]:query服务启动失败！");
        }

    }

    /**
     * 查找下一个
     */
    public static List<Integer> findNextPort(List<Integer> numbers, int count, int min, int max) {
        List<Integer> newList = new ArrayList<>();
        //将现有数加入BitSet
        BitSet bitSet = new BitSet(numbers.size());
        for (Integer number : numbers) {
            bitSet.set(number);
        }
        int len = max - min;
        double step = 0;
        if (len > 0 && count > 0) {
            step = Math.floor(len / count);
        }
        boolean random = false;
        //遍历查找
        for (int i = min; i <= max; i++) {
            if (!bitSet.get(i)) {
                //默认开始填充数组
                if (newList.size() == 0 && !random && step > count) {
                    random = true;
                    i = new Random().nextInt(max - min + 1) + min - count - 1;
                    continue;
                } else {
                    newList.add(i);
                }
            }
            if (newList.size() >= count) {
                break;
            }
        }
        return newList;
    }


}
