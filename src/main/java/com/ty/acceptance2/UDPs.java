package com.ty.acceptance2;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.List;
import java.util.TimerTask;

/**
 * UDP工具类
 */
public class UDPs {

    /**
     * 等待ACK消息
     *
     * @param head 当前host信息
     */
    public static void waitForACK(Head head) throws IOException {
        // 获取rcv_data
        byte[] bytes = new byte[1024];
        DatagramPacket packet = makePacket(bytes);
        // 设置超时时间
        head.getSocket().setSoTimeout(Head.TIMEOUT);
        head.getSocket().receive(packet);
        // 获取ack
        String ack = new String(packet.getData(), 0, packet.getLength()).trim();
        int ACK = Integer.parseInt(ack);
        System.out.println("收到ACK=" + ack + "\t时间=" + System.currentTimeMillis());
        // 累计确认
        if ((ACK - head.getBase() + Head.SEQ_NUM_LIMIT) % Head.SEQ_NUM_LIMIT < Head.WINDOW_SIZE) {
            // 重设it_base和base
            head.setBaseIterator(head.getBaseIterator() + (ACK - head.getBase() + 1) % Head.SEQ_NUM_LIMIT);
            head.setBase((ACK + 1) % Head.SEQ_NUM_LIMIT);
            // 此时窗口为空，则停止计时
            if (head.getBase() == head.getNextSeqNum())
                stopTimer(head);
            else startTimer(head); // 非空则计时重置
        }
    }

    /**
     * udt_send
     *
     * @param data       要发送的数据集合
     * @param head       当前host对应的信息
     * @param targetHost 目标host
     * @param targetPort 目标port
     */
    public static void send(List<String> data, Head head, InetAddress targetHost, int targetPort) throws IOException {
        // 发送的数据数量不超过窗口大小，并且数据还未发完则循环继续
        while ((head.getNextSeqNum() - head.getBase() + Head.SEQ_NUM_LIMIT) % Head.SEQ_NUM_LIMIT < Head.WINDOW_SIZE && head.getPacketsIterator() < data.size()) {
            // 发送nextSeqNum(packetsIterator)对应的数据
            DatagramPacket sendPacket = makePacket(data.get(head.getPacketsIterator()), targetHost, targetPort, head);
            head.getSocket().send(sendPacket);
            System.out.println("发送数据包序号=" + head.getPacketsIterator() + "\t序列号sequenceNumber=" + head.getNextSeqNum() + "\t时间=" + System.currentTimeMillis());
            // 如果当前是窗口的第一个元素，要开启计时器
            if (head.getNextSeqNum() == head.getBase()) {
                startTimer(head);
            }
            // 窗口扩张
            head.setNextSeqNum((head.getNextSeqNum() + 1) % Head.SEQ_NUM_LIMIT);
            head.setPacketsIterator(head.getPacketsIterator() + 1);
        }
    }

    /**
     * make_pkt(发送方)
     *
     * @param data 数据
     * @param host 目的地址
     * @param port 目的端口
     * @return 打包好的数据包
     */
    public static DatagramPacket makePacket(String data, InetAddress host, int port, Head head) {
        byte[] dataToSend = ("发送数据包序号=" + head.getPacketsIterator() + "\t序列号sequenceNumber=" + head.getNextSeqNum() + "\t时间=" + System.currentTimeMillis()).getBytes();
        return new DatagramPacket(dataToSend, dataToSend.length, host, port);
    }

    /**
     * make_pkt(接收方)
     *
     * @param data 数据
     * @return 打包好的数据包
     */
    public static DatagramPacket makePacket(byte[] data) {
        return new DatagramPacket(data, data.length);
    }

    /**
     * 开启计时器
     *
     * @param head host对应的信息
     */
    public static void startTimer(Head head) {
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
     *
     * @param head host对应的信息
     */
    public static void stopTimer(Head head) {
        head.getTimer().cancel();
        head.setTimer();
    }
}
