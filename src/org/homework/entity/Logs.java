package org.homework.entity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Scanner;

/**
 * 日志管理类
 */
public class Logs {
    File file;
    Integer port;

    public Logs(Integer port) {
        this.port = port;
        System.out.println("[query]:打开文件：" + port + ".log");
        file = new File(port + ".log");
        try {
            file.createNewFile();
        } catch (IOException e) {
            System.out.println("[query]:" + file.getName() + "文件已经存在！");
        }

    }

    /**
     * 将text写入到log最后一行
     */
    public void writeInfo(String text) {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true)))) {
            StringBuilder stringBuilder = new StringBuilder();
            long timestamp = System.currentTimeMillis();
            stringBuilder.append(timestamp).append(" ");
            stringBuilder.append(text);
            stringBuilder.append("\n");
            bw.write(stringBuilder.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String query(String keyword) {

        StringBuilder result = new StringBuilder();
        try (Scanner scanner = new Scanner(file)) {

            System.out.println("[query]:开始查询文件");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.contains(keyword)) {
                    result.append(line).append("\r\n");

                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("[queryError]:找不到log.");
        }
        return result.toString();
    }


}
