package main;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class ProxyServer {
    private static final Set<String> bannedUsers = new HashSet<>();

    static {
        bannedUsers.add("127.0.0.1"); // 本机演示用这个演示
        bannedUsers.add("127.0.0.2");
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8888);
        System.out.println("------------------- 代理服务器启动：8888-------------------");
        while (true) {
            // 与客户端的socket
            Socket clientSocket = serverSocket.accept();
//            clientSocket.setSoTimeout(5000);
            // 检查该客户端是否被禁止访问
            System.out.println("客户端：" + clientSocket.getInetAddress());
            if (bannedUsers.contains(clientSocket.getInetAddress().getHostAddress())) {
                PrintWriter response = new PrintWriter(clientSocket.getOutputStream(), true);
                response.println("你被禁止访问该代理服务器");
                System.out.println("客户端：" + clientSocket.getInetAddress() + "被禁止访问");
                continue;
            }
            // 处理客户端请求
            new Thread(new ProxyClient(clientSocket)).start();
        }
    }
}
