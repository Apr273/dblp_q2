package org.homework.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommonServer {

    public static Daemon daemon;

    public static void main(String[] args) {
        ExecutorService exec = Executors.newCachedThreadPool();
        //取随机端口，count控制端口数
        List<Integer> ids = new ArrayList<>();
        Collections.sort(ids);
        List<Integer> nextPort = Query.findNextPort(ids, 3, 50000, 60000);
        int port = nextPort.stream().findFirst().orElseThrow(() -> new RuntimeException("没有多余的端口"));
        //启动daemon服务
        daemon = new Daemon(port);
        //多线程 启动query服务
        exec.execute(new Thread(new Query(port)));
        console();
    }


    private static void console() {
        System.out.println("成功加入组服务，进入server控制台");
        System.out.println("指令列表：");
        System.out.println("check：显示组成员");
        System.out.println("exit： 退出组服务");
        Scanner scan = new Scanner(System.in);
        while (true) {
            System.out.println("");
            System.out.println("请输入指令：");
            String op = "";

            if (scan.hasNextLine())
                op = scan.nextLine();

            String[] words = op.trim().split("\\s+");

            if (words.length > 1) {
                System.out.println("指令格式错误！");
                continue;
            }

            if (words[0].equals("check")) {
                if (daemon.inGroup)
                    daemon.showGroup();
                else {
                    System.out.println("daemon未成功加入！");
                    daemon.joinGroup();
                }

            }

            else if (words[0].equals("exit")) {
                if (daemon.inGroup) {
                    daemon.leave();
                    return;
                } else {
                    System.out.println("daemon已经不在组中！");
                }
                return;
            }

            else {
                System.out.println("指令格式错误！");
                continue;
            }
        }
    }
}
