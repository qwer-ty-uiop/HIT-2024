package com.ty.acceptance2;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

public class Client {
    private static final Head head = new Head();

    public static void main(String[] args) throws IOException, InterruptedException {
        InetAddress targetAddress = InetAddress.getByName("localhost");
        int targetPort = Head.SERVER_PORT;
        head.setSocket("client");
        head.setFileInputPath("src/document/input/clientInput.txt");
        head.setFileOutputPath("src/document/output/GBNClientReceived.txt");
        GBN gbn = new GBN(head);

        // 客户端接收数据
        gbn.receive();
        // 客户端发数据
        List<String> data = head.getData();
        gbn.send(data,targetAddress,targetPort);
        System.exit(0);
    }
}
