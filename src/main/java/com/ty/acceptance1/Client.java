package com.ty.acceptance1;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class Client {
    private static final int SERVER_PORT = 23456;
    private static final int CLIENT_PORT = 12345;
    private static final double LOSS_PROBABILITY = 0.2;
    private static final int SEQ_NUM_LIMIT = 8; // 3位序号空间，序列号范围 0~7

    private static DatagramSocket clientSocket;

    public static void main(String[] args) throws IOException {
        clientSocket = new DatagramSocket(CLIENT_PORT);
        InetAddress serverAddress = InetAddress.getByName("localhost");
        int expectedSeqNum = 0;
        Random random = new Random(); // 模拟丢包
        while (true) {
            byte[] receive = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receive, receive.length);
            clientSocket.receive(receivePacket);
            int len = receivePacket.getLength();
            String receivedData = new String(receivePacket.getData(), 0, len);
            if ("Finish".equals(receivedData)) {
                System.out.println("传输完成");
                System.exit(0);
            }
            String data = receivedData.split("\t")[0];
            // 获取seqNum
            int seqNum;
            if (receivedData.contains("sequenceNumber=")) {
                String sequenceNumber = receivedData.split("\t")[1];
                seqNum = Integer.parseInt(sequenceNumber.substring(sequenceNumber.indexOf("sequenceNumber=") + "sequenceNumber=".length()));
            } else {
                System.out.println("数据包损坏");
                continue;
            }

            String date = new SimpleDateFormat().format(new Date());
            System.out.println("接收数据包=" + data + "\t序列号=" + seqNum + "\t时间=" + date);
            // 模拟丢包
            if (random.nextDouble() < LOSS_PROBABILITY) {
                System.out.println("数据包" + data + "-" + seqNum + "丢包");
                continue;
            }
            // 发送ack
            if (seqNum == expectedSeqNum) {
                String ack = Integer.toString(seqNum);
                byte[] ackBytes = ack.getBytes();
                DatagramPacket ackPacket = new DatagramPacket(ackBytes, ackBytes.length, serverAddress, SERVER_PORT);
                clientSocket.send(ackPacket);
                System.out.println("发送ACK=" + ack);
                expectedSeqNum = (expectedSeqNum + 1) % SEQ_NUM_LIMIT;
            } else {
                System.out.println("乱序到达，丢弃数据包=" + data + "\t序列号=" + seqNum + "\t期望序列号=" + expectedSeqNum);
            }
        }
    }
}
