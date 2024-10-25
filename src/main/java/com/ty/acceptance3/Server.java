package com.ty.acceptance3;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Server {
    public static void main(String[] args) throws SocketException, UnknownHostException {
        SR sr = new SR(9999, "server");
        sr.setTargetHost(InetAddress.getLocalHost());
        sr.setTargetPort(7777);
        sr.send("", sr.getTargetHost(), 7777);
    }
}