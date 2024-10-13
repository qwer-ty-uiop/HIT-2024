//package main;
//
//import java.io.*;
//import java.net.MalformedURLException;
//import java.net.Socket;
//import java.net.URL;
//
//public class HandleThread implements Runnable {
//    Socket clientSocket;
//    String requestMethod, url, lastModified, targetHost;
//    URL targetUrl;
//    int targetPort;
//
//    public HandleThread(Socket clientSocket) {
//        // 获取客户端socket
//        this.clientSocket = clientSocket;
//    }
//
//    @Override
//    public void run() {
//        try (
//                // 从客户端读取请求
//                BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
//        ) {
//            // 读取客户端的 HTTP 请求的第一行，即请求行
//            String requestLine = clientReader.readLine();
//            if (requestLine == null || requestLine.isEmpty())
//                return;
//            System.out.println(Thread.currentThread().getName() + "客户端请求行: " + requestLine);
//
//            // 解析请求行中的 URL，提取目标服务器地址
//            getServerAddress(requestLine);
//
//            // 创建与目标服务器的连接
//            try (Socket serverSocket = new Socket(targetHost, targetPort)) {
//                // 创建传输通道
//                BufferedWriter serverWriter = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream()));
//                BufferedInputStream serverReader = new BufferedInputStream(serverSocket.getInputStream());
//                // 将客户端请求转发给目标服务器
//                sendToServer(serverWriter);
//                // 读取目标服务器的响应并转发给客户端
//                printToBrowser(serverReader);
//
//                System.out.println(Thread.currentThread().getName() + "网址: " + url + "响应成功！");
//            } catch (IOException e) {
//                System.out.println(Thread.currentThread().getName() + "目标服务器连接失败: " + e.getMessage());
//            }
//        } catch (IOException e) {
//            System.out.println(Thread.currentThread().getName() + "处理客户端请求失败: " + e.getMessage());
//        } finally {
//            // 关闭连接
//            try {
//                clientSocket.close();
//            } catch (IOException e) {
//                System.out.println(Thread.currentThread().getName() + "关闭客户端套接字失败: " + e.getMessage());
//            }
//        }
//    }
//
//    /**
//     * 获取客户端请求的服务端地址
//     * @param requestLine 客户端请求行
//     * @throws MalformedURLException 创建的url可能不存在，或者url错误
//     */
//    private void getServerAddress(String requestLine) throws MalformedURLException {
//        String[] requestParts = requestLine.split(" ");
//        if (requestParts.length < 2)
//            return;
//
//        requestMethod = requestParts[0];
//        url = requestParts[1];
//        // 默认的最后修改时间，用于文件不存在的时候
//        lastModified = "Thu, 01 Jul 1970 20:00:00 GMT";
//        // 从 URL 中提取目标服务器的主机名和端口号
//        targetUrl = new URL(url);
//        targetHost = targetUrl.getHost();
//        targetPort = targetUrl.getPort() == -1 ? 80 : targetUrl.getPort();  // 默认端口为80
//
//        System.out.println(Thread.currentThread().getName() + "目标服务器地址: " + targetHost + ":" + targetPort);
//
//    }
//
//    /**
//     * 将客户端的报文发送给服务端
//     * @param serverWriter 向服务器写数据的通道
//     */
//    private void sendToServer(BufferedWriter serverWriter) throws IOException {
//        String request = requestMethod + " " + url + " HTTP/1.1" + "\r\n" +
//                "HOST: " + targetHost + "\n" +
//                "Accept:text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\n" +
//                "Accept-Encoding:gzip, deflate, sdch\n" +
//                "Accept-Language:zh-CN,zh;q=0.8\n" +
//                "If-Modified-Since: " + lastModified + "\n" +
//                "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.2 Safari/605.1.15\n" +
//                "Encoding:UTF-8\n" +
//                "Connection:keep-alive" + "\n" +
//                "\n";
//        serverWriter.write(request);
//        serverWriter.flush();
//    }
//
//    /**
//     * 将服务端的数据发送给客户端
//     * @param serverReader 从服务器读数据的通道
//     */
//    private void printToBrowser(BufferedInputStream serverReader) throws IOException {
//        OutputStream outToBrowser = clientSocket.getOutputStream();
//        byte[] response = new byte[1024];
//        while (serverReader.read(response) != -1) {
//            outToBrowser.write(response);
//        }
//        outToBrowser.flush();
//    }
//}
