package other;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MultiThreadedProxyServer {

    // 代理服务器监听的端口
    private static final int PROXY_PORT = 8080;
    // 用于缓存的 Map，存储 URL 和对应的文件内容及修改时间
    private static final Map<String, CachedObject> cache = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket proxySocket = new ServerSocket(PROXY_PORT)) {
            System.out.println("代理服务器启动，正在监听端口：" + PROXY_PORT);

            while (true) {
                // 接受客户端连接
                Socket clientSocket = proxySocket.accept();
                System.out.println("客户端连接：" + clientSocket.getInetAddress());
                // 创建子线程处理客户端请求
                new Thread(new HandleClientRequest(clientSocket)).start();
            }
        } catch (IOException e) {
            System.out.println("代理服务器错误: " + e.getMessage());
        }
    }

    private static class HandleClientRequest implements Runnable {
        private Socket clientSocket;

        public HandleClientRequest(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    BufferedWriter clientWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
            ) {
                // 读取客户端的请求
                String requestLine = clientReader.readLine();
                if (requestLine == null || requestLine.isEmpty()) {
                    return;
                }
                System.out.println(Thread.currentThread().getName() + " 客户端请求行: " + requestLine);

                // 解析请求行中的 URL
                String[] requestParts = requestLine.split(" ");
                if (requestParts.length < 2) {
                    return;
                }
                String url = requestParts[1];
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://" + url;
                }
                URL targetUrl = new URL(url); // 确保 URL 格式正确
                String targetHost = targetUrl.getHost();
                int targetPort = targetUrl.getPort() == -1 ? 80 : targetUrl.getPort(); // 默认 HTTP 端口

                CachedObject cachedObject = cache.get(url);
                if (cachedObject != null) {
                    // 从缓存中获取对象并提取最后修改时间
                    String lastModified = cachedObject.getLastModified();
                    System.out.println(Thread.currentThread().getName() + " 使用缓存的对象: " + url);
                    clientWriter.write(requestLine + "\r\n");
                    clientWriter.write("If-Modified-Since: " + lastModified + "\r\n");
                } else {
                    System.out.println(Thread.currentThread().getName() + " 缓存中没有对象: " + url);
                    clientWriter.write(requestLine + "\r\n");
                }

                // 转发请求头
                String headerLine;
                while (!(headerLine = clientReader.readLine()).isEmpty()) {
                    clientWriter.write(headerLine + "\r\n");
                }
                clientWriter.write("\r\n");
                clientWriter.flush();

                // 创建与目标服务器的连接
                try (Socket serverSocket = new Socket(targetHost, targetPort);
                     BufferedReader serverReader = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
                     BufferedWriter serverWriter = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream()))) {

                    // 将请求转发给目标服务器
                    serverWriter.write(requestLine + "\r\n");
                    serverWriter.flush();

                    // 读取目标服务器的响应并转发给客户端
                    String responseLine;
                    StringBuilder responseContent = new StringBuilder();
                    while ((responseLine = serverReader.readLine()) != null) {
                        responseContent.append(responseLine).append("\r\n");
                        clientWriter.write(responseLine + "\r\n");
                    }
                    clientWriter.flush();

                    // 将响应内容缓存到代理服务器
                    cache.put(url, new CachedObject(responseContent.toString(), getCurrentDate()));
                    System.out.println(Thread.currentThread().getName() + " 响应成功转发给客户端并缓存");
                } catch (IOException e) {
                    System.out.println(Thread.currentThread().getName() + " 目标服务器连接失败: " + e.getMessage());
                }
            } catch (IOException e) {
                System.out.println(Thread.currentThread().getName() + " 处理客户端请求失败: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.out.println(Thread.currentThread().getName() + " 关闭客户端套接字失败: " + e.getMessage());
                }
            }
        }

        private String getCurrentDate() {
            return String.valueOf(System.currentTimeMillis()); // 使用时间戳作为最后修改时间
        }
    }

    private static class CachedObject {
        private String content;
        private String lastModified;

        public CachedObject(String content, String lastModified) {
            this.content = content;
            this.lastModified = lastModified;
        }

        public String getLastModified() {
            return lastModified;
        }
    }
}
