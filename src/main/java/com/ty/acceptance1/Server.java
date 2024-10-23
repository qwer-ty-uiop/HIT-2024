package com.ty.acceptance1;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;

public class Server {
    private static final int SERVER_PORT = 23456;
    private static final int CLIENT_PORT = 12345;
    private static final int WINDOW_SIZE = 4;
    private static final int TIMEOUT = 1000;
    private static final int SEQ_NUM_LIMIT = 8; // 3位序号空间，序列号范围 0~7

    private static int base = 0;
    private static int nextSeqNum = 0;
    private static int packetsIterator = 0;
    private static int baseIterator = 0;
    private static DatagramSocket serverSocket;
    private static Timer timer = new Timer();

    public static void main(String[] args) throws IOException {
        serverSocket = new DatagramSocket(SERVER_PORT);
        InetAddress clientAddress = InetAddress.getByName("localhost");

        // 模拟需要发送的数据包 （三十个数据）
        String[] DATA_PACKETS = {"data1", "data2", "data3", "data4", "data5", "data6", "data7", "data8", "data9", "data10", "data11", "data12", "data13", "data14", "data15", "data16", "data17", "data18", "data19", "data20", "data21", "data22", "data23", "data24", "data25", "data26", "data27", "data28", "data29", "data30"};

        while (baseIterator < DATA_PACKETS.length) {
            // 尽可能发送packet
            while ((nextSeqNum - base + SEQ_NUM_LIMIT) % SEQ_NUM_LIMIT < WINDOW_SIZE && packetsIterator < DATA_PACKETS.length) {
                // send_pkt()
                String data = DATA_PACKETS[packetsIterator] + "\tsequenceNumber=" + nextSeqNum + "\tdataPacketNumber=" + packetsIterator;
                DatagramPacket sendPacket = new DatagramPacket(data.getBytes(), data.getBytes().length, clientAddress, CLIENT_PORT);
                serverSocket.send(sendPacket);
                System.out.println("发送数据包" + (packetsIterator + 1) + "\t序列号=" + nextSeqNum + "\t时间=" + System.currentTimeMillis());
                // start_timer
                if (base == nextSeqNum) {
                    startTimer();
                }
                nextSeqNum = (nextSeqNum + 1) % SEQ_NUM_LIMIT;
                packetsIterator++;
            }
            // 接收ACK
            try {
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.setSoTimeout(TIMEOUT);
                serverSocket.receive(receivePacket);

                String ack = new String(receivePacket.getData()).trim();
                int ackNum = Integer.parseInt(ack);
                System.out.println("接收到ACK: 序列号=" + ackNum + "\t时间=" + System.currentTimeMillis());
                if ((ackNum - base + 1 + SEQ_NUM_LIMIT) % SEQ_NUM_LIMIT <= WINDOW_SIZE) {
                    baseIterator += (ackNum - base + 1 + SEQ_NUM_LIMIT) % SEQ_NUM_LIMIT;
                    base = (ackNum + 1) % SEQ_NUM_LIMIT;
                    if (base == nextSeqNum)
                        stopTimer();
                    else
                        startTimer(); // 重启计时器
                }
            } catch (Exception e) {
//                System.out.println("超时，从序列号=" + base + " 开始重传");
                nextSeqNum = base;
                packetsIterator = baseIterator;
            }
        }
        byte[] data = "Finish".getBytes();
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), CLIENT_PORT);
        serverSocket.send(datagramPacket);
        System.out.println("传输完成");
        serverSocket.close();
        System.exit(0);
    }

    private static void startTimer() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("超时，从序列号=" + base + " 开始重传");
                nextSeqNum = base;
                packetsIterator = baseIterator;
            }
        }, TIMEOUT);
    }

    private static void stopTimer() {
        timer.cancel();
        timer = new Timer();
    }
}