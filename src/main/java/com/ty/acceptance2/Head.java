package com.ty.acceptance2;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.rmi.UnknownHostException;
import java.util.Timer;

public class Head {
    public static final int SERVER_PORT = 23456;
    public static final int CLIENT_PORT = 12345;
    public static final int WINDOW_SIZE = 4;
    public static final int SEQ_NUM_LIMIT = 8;
    public static final double LOSS_PROBABILITY = 0.2;
    public static final int TIMEOUT = 3000;

    private int base = 0;
    private int nextSeqNum = 0;
    private int packetsIterator = 0;
    private int baseIterator = 0;
    private DatagramSocket socket;
    private Timer timer = new Timer();
    private String fileInputPath;

    public Head() {

    }

    public String getFileInputPath() {
        return fileInputPath;
    }

    public void setFileInputPath(String fileInputPath) {
        this.fileInputPath = fileInputPath;
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public void setSocket(String id) throws SocketException {
        if ("client".equals(id)) {
            socket = new DatagramSocket(CLIENT_PORT);
        } else if ("server".equals(id)) {
            socket = new DatagramSocket(SERVER_PORT);
        } else throw new SocketException("创建Socket失败，没有名为" + id + "的主机对应的port");
    }

    public int getBase() {
        return base;
    }

    public void setBase(int base) {
        this.base = base;
    }

    public int getNextSeqNum() {
        return nextSeqNum;
    }

    public void setNextSeqNum(int nextSeqNum) {
        this.nextSeqNum = nextSeqNum;
    }

    public int getPacketsIterator() {
        return packetsIterator;
    }

    public void setPacketsIterator(int packetsIterator) {
        this.packetsIterator = packetsIterator;
    }

    public int getBaseIterator() {
        return baseIterator;
    }

    public void setBaseIterator(int baseIterator) {
        this.baseIterator = baseIterator;
    }

    public Timer getTimer() {
        return timer;
    }

    public void setTimer() {
        timer = new Timer();
    }
}
