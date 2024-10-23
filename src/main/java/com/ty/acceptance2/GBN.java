package com.ty.acceptance2;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.List;
import java.util.Random;
import java.util.TimerTask;

/**
 * UDP工具类
 */
public class GBN {

    private final Head head; // 信息类
    private final Random random = new Random();

    public GBN(Head head) {
        this.head = head;
    }

    /**
     * 发送ACK
     *
     * @param seqNum  收到的seqNum
     * @param address 目的地址
     * @param port    目的端口号
     */
    private void sendACK(int seqNum, InetAddress address, int port) throws IOException {
        System.out.println("发送ACK=" + seqNum);
        String sendData = ("ACK=" + seqNum);
        DatagramPacket sendPacket = makePacket(sendData, address, port);
        head.getSocket().send(sendPacket);
    }

    /**
     * 等待ACK消息
     */
    public void waitForACK() throws IOException {
        // 获取rcv_data
        byte[] bytes = new byte[1024];
        DatagramPacket packet = makePacket(bytes);
        head.getSocket().receive(packet);

        // 获取ack
        String ack = new String(packet.getData(), 0, packet.getLength());
        int ACK = Integer.parseInt(ack.split("data=")[1].split("=")[1]);
        System.out.println("收到ACK=" + ACK + "\t时间=" + System.currentTimeMillis());

        // 累计确认
        if ((ACK - head.getBase() + 1 + Head.SEQ_NUM_LIMIT) % Head.SEQ_NUM_LIMIT <= Head.WINDOW_SIZE) {
            // 重设it_base和base
            head.setBaseIterator(head.getBaseIterator() + (ACK - head.getBase() + 1) % Head.SEQ_NUM_LIMIT);
            head.setBase((ACK + 1) % Head.SEQ_NUM_LIMIT);
            // 此时窗口为空，则停止计时
            if (head.getBase() == head.getNextSeqNum())
                stopTimer();
            else startTimer(); // 非空则计时重置
        }
    }

    /**
     * 接收数据并输出到文件中
     */
    public void receive() throws InterruptedException {
        try (FileOutputStream out = new FileOutputStream(head.getFileOutputPath())) {
            byte[] outputBuffer = new byte[Head.PACKET_SIZE];
            int expectSeqNum = 0;
            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(outputBuffer, outputBuffer.length);
                head.getSocket().receive(receivePacket);
                // 获取源地址信息
                InetAddress address = receivePacket.getAddress();
                int port = receivePacket.getPort();
                String receiveData = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                // 解析序列号、发送时间和数据
                int seqNum = Integer.parseInt(receiveData.split("\t")[0].split("=")[1]);
                long sendTime = Long.parseLong(receiveData.split("\t")[1].split("time=")[1]);
                String data = receiveData.split("data=")[1];
                // 传输结束，退出
                if ("Finish".equals(data)) {
                    System.err.println("传输结束");
                    Thread.sleep(10000);
                    return;
                }
                // 模拟丢包
                if (random.nextDouble() < Head.LOSS_PROBABILITY) {
                    System.out.println("丢包: seq=" + seqNum + "\tsendTime=" + sendTime + "\tdata=" + data);
                    continue;
                }
                System.out.println("接收数据为: seq=" + seqNum + "\tsendTime=" + sendTime + "\tdata=" + data);
                // 按序收到数据
                if (seqNum == expectSeqNum) {
                    out.write(data.getBytes());
                    out.write("\n".getBytes());
                    sendACK(seqNum, address, port);
                    expectSeqNum = (expectSeqNum + 1) % Head.SEQ_NUM_LIMIT;
                } else {
                    System.out.println("收到乱序数据包，丢弃。期待序列号 seq=" + expectSeqNum);
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

    }

    /**
     * udt_send
     *
     * @param data       要发送的数据集合
     * @param targetHost 目标host
     * @param targetPort 目标port
     */
    public void sendIfWindowNotFull(List<String> data, InetAddress targetHost, int targetPort) throws IOException {
        // 发送的数据数量不超过窗口大小，并且数据还未发完则循环继续
        while ((head.getNextSeqNum() - head.getBase() + Head.SEQ_NUM_LIMIT) % Head.SEQ_NUM_LIMIT < Head.WINDOW_SIZE && head.getPacketsIterator() < data.size()) {
            // 发送nextSeqNum(packetsIterator)对应的数据
            DatagramPacket sendPacket = makePacket(data.get(head.getPacketsIterator()), targetHost, targetPort);
            head.getSocket().send(sendPacket);
            System.out.println("seq=" + head.getNextSeqNum() + "\ttime=" + System.currentTimeMillis() + "\tdata=" + data.get(head.getPacketsIterator()));
            // 如果当前是窗口的第一个元素，要开启计时器
            if (head.getNextSeqNum() == head.getBase()) {
                startTimer();
            }
            // 窗口扩张
            head.setNextSeqNum((head.getNextSeqNum() + 1) % Head.SEQ_NUM_LIMIT);
            head.setPacketsIterator(head.getPacketsIterator() + 1);
        }
    }

    /**
     * 发送数据包
     *
     * @param data       数据包
     * @param targetHost 目标地址
     * @param targetPort 目标端口
     */
    public void send(List<String> data, InetAddress targetHost, int targetPort) throws IOException, InterruptedException {
        // 设置超时时间，记得复原设置，不然接收时会出现超时发生死锁
        head.getSocket().setSoTimeout(Head.TIMEOUT);
        while (head.getBaseIterator() < data.size()) {
            try {
                sendIfWindowNotFull(data, targetHost, targetPort);
                waitForACK();
            } catch (Exception e) {
                System.out.println("超时重传，从序列号 seq=" + head.getBase() + " 开始重传");
                head.setNextSeqNum(head.getBase());
                head.setPacketsIterator(head.getBaseIterator());
                startTimer();
            }
        }
        // 传输结束，发送消息
        DatagramPacket sendPacket = makePacket("Finish", targetHost, targetPort);
        head.getSocket().send(sendPacket);
        System.err.println("传输结束");
        // 复原设置
        head.getSocket().setSoTimeout(0);
        Thread.sleep(5000);
    }

    /**
     * make_pkt(发送数据包/ACK)
     *
     * @param data 数据
     * @param host 目的地址
     * @param port 目的端口
     * @return 打包好的数据包
     */
    public DatagramPacket makePacket(String data, InetAddress host, int port) {
        byte[] dataToSend = ("seq=" + head.getNextSeqNum() + "\ttime=" + System.currentTimeMillis() + "\tdata=" + data).getBytes();
        return new DatagramPacket(dataToSend, dataToSend.length, host, port);
    }

    /**
     * make_pkt(接收ACK)
     *
     * @param data 数据
     * @return 打包好的数据包
     */
    public DatagramPacket makePacket(byte[] data) {
        return new DatagramPacket(data, data.length);
    }

    /**
     * 开启计时器
     */
    private void startTimer() {
        head.getTimer().schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("超时重传\t" + "base=" + head.getBase());
                head.setNextSeqNum(head.getBase());
                head.setPacketsIterator(head.getBaseIterator());
            }
        }, Head.TIMEOUT);
    }

    /**
     * 关闭计时器
     */
    private void stopTimer() {
        head.getTimer().cancel();
        head.setTimer();
    }


}
