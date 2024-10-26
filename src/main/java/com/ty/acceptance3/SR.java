package com.ty.acceptance3;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;

public class SR {
    private static final Random random = new Random();

    public static final int SEND_WINDOW_SIZE = 8;
    public static final int RECEIVE_WINDOW_SIZE = 8;
    public static final int SEQ_NUM_LIMIT = 16; // 接收窗口 + 发送窗口 <= 序列号数量
    public static final int PACKET_SIZE = 1024;
    public static final int TIMEOUT = 1000;
    public static final double LOSS_PROBABILITY = 0.2;

    private final DatagramSocket socket;
    private final String[] receiveWindow = new String[SEQ_NUM_LIMIT]; // 接收窗口
    private final Timer[] timers = new Timer[SEQ_NUM_LIMIT]; // 窗口每个数据包都要用一个timer
    private final boolean[] isACK = new boolean[SEQ_NUM_LIMIT]; // 记录哪些被接收了
    private final List<String> dataSet = new ArrayList<>();

    private String fileInputPath;
    private String fileOutputPath;
    private int base = 0;           // 发送窗口基址
    private int nextSeq = 0;        // 发送窗口边缘
    private int receiveBase = 0;    // 接收窗口基址
    private int baseIndex = 0;      // 数据数组剩余数据的基址(对应base)
    private int nextIndex = 0;      // 数据数组索引(对应nextSeq)
    private InetAddress targetHost;
    private int targetPort;

    public SR(int port, String role) throws SocketException {
        socket = new DatagramSocket(port);
        fileInputPath = "src/document/input/" + role + "Input.txt";
        fileOutputPath = "src/document/output/SR" + role.substring(0, 1).toUpperCase() + role.substring(1) + "Output.txt";
        Arrays.fill(timers, new Timer());
    }

    /**
     * 接收数据
     *
     * @param fileOutputPath 输出路径
     */
    public void receive(String fileOutputPath) {
        receiveInit(fileOutputPath);
        try (FileOutputStream out = new FileOutputStream(this.fileOutputPath)) {

            while (true) {
                ReceiveMessage receiveMessage = new ReceiveMessage();
                int seq = receiveMessage.seq;
                int seqBase = receiveMessage.base;
                long time = receiveMessage.time;
                String data = receiveMessage.data;
                // 传输结束通知
                if ("Finish".equals(data)) {
                    System.err.println("传输完成");
                    Thread.sleep(5000);
                    return;
                }
                if (random.nextDouble() < LOSS_PROBABILITY) {
                    System.out.println("丢包:\tseq=" + seq + "\tsendTime=" + time + "\tdata=" + data);
                    continue;
                }
                System.out.println("收到数据:\tseq=" + seq + "\tsendTime=" + time + "\tdata=" + data);
                // 发送ACK
                sendACK(seq, seqBase);
                // 收到的序列号在窗口内，则缓存分组。不在窗口内的数据都是已经接受过的了
                if ((seq - receiveBase + SEQ_NUM_LIMIT) % SEQ_NUM_LIMIT < RECEIVE_WINDOW_SIZE)
                    receiveWindow[seq] = data;
                // 按序输出
                while (receiveWindow[receiveBase] != null) {
                    out.write(receiveWindow[receiveBase].getBytes());
                    out.write("\n".getBytes());
                    out.flush();
                    receiveWindow[receiveBase] = null;
                    receiveBase = (receiveBase + 1) % SEQ_NUM_LIMIT;
                }
            }
        } catch (Exception e) {
//            System.err.println(e.getMessage());
            e.printStackTrace();
        }

    }

    /**
     * 初始化接收窗口等参数
     *
     * @param fileOutputPath 输出路径
     */
    private void receiveInit(String fileOutputPath) {
        receiveBase = 0;
        if (!fileOutputPath.isEmpty()) this.fileOutputPath = fileOutputPath;
        Arrays.fill(receiveWindow, null);
    }


    /**
     * 发送ACK
     *
     * @param seq  接收到的序列号
     * @param base 接收的这个序列号在发送时对应的base
     */
    private void sendACK(int seq, int base) throws IOException {
        String ACK = "ACK=" + seq + "\tbase=" + base + "\ttime=" + System.currentTimeMillis();
        System.out.println("发送ACK:\t" + ACK);
        DatagramPacket ACKPacket = new DatagramPacket(ACK.getBytes(), ACK.length(), targetHost, targetPort);
        socket.send(ACKPacket);
    }

    /**
     * 发送数据
     *
     * @param dataFilePath 数据所在文件地址，字符串为空时用默认路径
     * @param targetHost   目的IP
     * @param targetPort   目的端口
     */
    public void send(String dataFilePath, InetAddress targetHost, int targetPort) {
        sendInit(dataFilePath, targetHost, targetPort);
        while (baseIndex < dataSet.size()) {
            try {
                sendIfWindowNotFull();
                waitForACK();
            } catch (Exception e) {
                throw new RuntimeException(e);
//                System.err.println(e.getMessage());
            }
        }
        DatagramPacket endFlag = makePacket("Finish");
        try {
            socket.send(endFlag);
            System.err.println("传输完成");
            Thread.sleep(5000);
        } catch (Exception e) {
            throw new RuntimeException(e);
//            System.err.println(e.getMessage());
        }
    }

    /**
     * 初始化发送数据要使用的变量
     *
     * @param dataFilePath 数据所在地址
     * @param targetHost   目的IP
     * @param targetPort   目的Port
     */
    private void sendInit(String dataFilePath, InetAddress targetHost, int targetPort) {
        base = 0;
        nextSeq = 0;
        baseIndex = 0;
        nextIndex = 0;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        fileInputPath = dataFilePath.isEmpty() ? fileInputPath : dataFilePath;
        dataSet.clear();
        try (BufferedReader in = new BufferedReader(new FileReader(fileInputPath))) {
            String line;
            while ((line = in.readLine()) != null) {
                dataSet.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 发送窗口中的可发数据
     */
    private void sendIfWindowNotFull() throws IOException {
        // 发送窗口未满且还有数据可发，就继续发送数据
        while ((nextSeq - base + SEQ_NUM_LIMIT) % SEQ_NUM_LIMIT < SEND_WINDOW_SIZE && nextIndex < dataSet.size()) {
            DatagramPacket dataPacket = makePacket(dataSet.get(nextIndex));
            socket.send(dataPacket);
            System.out.println("发送数据包:\tseq=" + nextSeq + "\tbase=" + base + "\ttime=" + System.currentTimeMillis() + "\tdata=" + dataSet.get(nextIndex));
            // 每个数据包又要用一个计时器监控
            startTimer(nextSeq, base, baseIndex);
            nextSeq = (nextSeq + 1) % SEQ_NUM_LIMIT;
            nextIndex++;
        }
    }

    /**
     * 等待ACK，接收ACK并处理
     */
    private void waitForACK() {
        ReceiveMessage receiveMessage = new ReceiveMessage();
        System.out.println("收到ACK消息:\tseqNum=" + receiveMessage.seq + "\tbase=" + receiveMessage.base + "\ttime=" + receiveMessage.time);
        // 关闭计时器，缓存ack
        stopTimer(receiveMessage.seq);
        isACK[receiveMessage.seq] = true;
        // 更新base、数据集合指针和发送窗口
        while (isACK[base]) {
            isACK[base] = false;
            base = (base + 1) % SEQ_NUM_LIMIT;
            baseIndex++;
        }
    }

    /**
     * 打包要发送的数据包
     *
     * @param data 传输的数据包的字节数组
     * @return 打包好的数据包
     */
    private DatagramPacket makePacket(String data) {
        // 添加seq等信息
        byte[] dataBytes = ("seq=" + nextSeq + "\tbase=" + base + "\ttime=" + System.currentTimeMillis() + "\tdata=" + data).getBytes();
        return new DatagramPacket(dataBytes, dataBytes.length, targetHost, targetPort);
    }

    /**
     * 打包要发送的数据包(重传时用)
     *
     * @param seq  序列号
     * @param base 基址
     * @param data 传输的数据包的字节数组
     * @return 打包好的数据包
     */
    private DatagramPacket makePacket(int seq, int base, String data) {
        // 添加seq等信息
        byte[] dataBytes = ("seq=" + seq + "\tbase=" + base + "\ttime=" + System.currentTimeMillis() + "\tdata=" + data).getBytes();
        return new DatagramPacket(dataBytes, dataBytes.length, targetHost, targetPort);
    }

    /**
     * 创捷接收数据包
     *
     * @param buffer 接收缓存
     * @return 接收数据包
     */
    private DatagramPacket makePacket(byte[] buffer) {
        return new DatagramPacket(buffer, buffer.length);
    }

    /**
     * 开启计时器
     *
     * @param seq       当前序列号
     * @param seqBase   当前基址，用于找dataSet中的数据
     * @param baseIndex 当前数据包在集合中的基地址
     */
    private void startTimer(int seq, int seqBase, int baseIndex) {
        stopTimer(seq); // 确保这个timer可以被正常调用
        timers[seq].schedule(new TimerTask() {
            @Override
            public void run() {
                int dataIndex = baseIndex + seq - seqBase;
                System.out.println("超时重传:\tseq=" + seq + "\tbase=" + seqBase + "\ttime=" + System.currentTimeMillis() + "\tdata=" + dataSet.get(dataIndex));
                try {
                    DatagramPacket sendPacket = makePacket(seq, seqBase, dataSet.get(dataIndex));
                    socket.send(sendPacket);
                    startTimer(seq, seqBase, baseIndex);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }, TIMEOUT);
    }

    /**
     * 停止计时器
     *
     * @param seq 当前序列号
     */
    private void stopTimer(int seq) {
        if (timers[seq] != null) {
            timers[seq].cancel();
            timers[seq].purge();
            timers[seq] = new Timer();
        }
    }

    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
    }

    public InetAddress getTargetHost() {
        return targetHost;
    }

    public void setTargetHost(InetAddress targetHost) {
        this.targetHost = targetHost;
    }

    public int getTargetPort() {
        return this.targetPort;
    }

    /**
     * 处理收到的数据
     */
    private class ReceiveMessage {
        int seq;
        int base;
        long time;
        String data;

        ReceiveMessage() {
            byte[] buffer = new byte[PACKET_SIZE];
            DatagramPacket packet = makePacket(buffer);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
            // 解析数据
            String[] dataPackets = new String(packet.getData(), 0, packet.getLength()).split("\t");
            seq = Integer.parseInt(dataPackets[0].split("=")[1]);
            base = Integer.parseInt(dataPackets[1].split("=")[1]);
            time = Long.parseLong(dataPackets[2].split("=")[1]);
            data = dataPackets.length > 3 ? dataPackets[3].split("=")[1] : "";
        }

    }
}
