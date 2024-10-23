package com.ty.acceptance2;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

public class Server {
    private static final Head head = new Head();

    public static void main(String[] args) throws IOException, InterruptedException {
        InetAddress targetAddress = InetAddress.getByName("localhost");
        int targetPort = Head.CLIENT_PORT;
        head.setSocket("server");
        head.setFileInputPath("src/document/input/serverInput.txt");
        head.setFileOutputPath("src/document/output/serverReceived.txt");
        GBN gbn = new GBN(head);
        // 服务器发数据
        List<String> data = head.getData();
        gbn.send(data, targetAddress, targetPort);
        // 服务器接收数据
        gbn.receive();
        System.exit(0);
    }
}
