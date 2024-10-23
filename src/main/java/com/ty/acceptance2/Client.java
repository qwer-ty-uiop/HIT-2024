package com.ty.acceptance2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Client {
    private static final Head head = new Head();

    public static void main(String[] args) throws Exception {
        InetAddress targetAddress = InetAddress.getByName("localhost");
        int targetPort = Head.SERVER_PORT;
        head.setSocket("client");
        // 输入传输文件的路径
        Scanner scanner = new Scanner(System.in);
        System.out.println("输入想要传输文件的路径: ");
        head.setFileInputPath(scanner.nextLine());
        File file = new File(scanner.nextLine());
        BufferedReader reader = new BufferedReader(new FileReader(file));
        // 获取数据
        List<String> data = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null)
            data.add(line);
        // 开始传输
        while (head.getBaseIterator() < data.size()) {
            UDPs.send(data, head, targetAddress, targetPort);
            UDPs.waitForACK(head);
        }
        System.out.println("传输完毕！");
    }
}
