package com.ty.acceptance3;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Client {
    public static void main(String[] args) throws SocketException, UnknownHostException {
        SR sr = new SR(7777, "client");
        sr.setTargetHost(InetAddress.getLocalHost());
        sr.setTargetPort(9999);
        // Client接收Server的信息
        sr.receive("");
        // Client发送信息给Server
        sr.send("", sr.getTargetHost(), sr.getTargetPort());
        System.exit(0);
    }
}
