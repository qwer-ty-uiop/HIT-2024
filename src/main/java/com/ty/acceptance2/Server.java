package com.ty.acceptance2;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private static final Head head = new Head();

    public static void main(String[] args) throws UnknownHostException, SocketException {
        InetAddress targetAddress = InetAddress.getByName("localhost");
        int targetPort = Head.CLIENT_PORT;
        head.setSocket("server");
//        // 输入传输文件的路径
//        Scanner scanner = new Scanner(System.in);
//        System.out.println("输入想要传输文件的路径: ");
//        head.setFileInputPath(scanner.nextLine());
//        File file = new File(scanner.nextLine());
//        BufferedReader reader = new BufferedReader(new FileReader(file));
//        // 获取数据
//        List<String> data = new ArrayList<>();
//        String line;
//        while ((line = reader.readLine()) != null)
//            data.add(line);

        List<String> data = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            data.add("data" + i);
        }

        // 开始传输
        while (head.getBaseIterator() < data.size()) {
            try {
                UDPs.send(data, head, targetAddress, targetPort);
                // 可能超时
                UDPs.waitForACK(head);
            } catch (Exception e) {
                // 准备超时重传
                head.setNextSeqNum(head.getBase());
                head.setPacketsIterator(head.getBaseIterator());
            }
        }
        System.out.println("传输完毕！");

    }
}
