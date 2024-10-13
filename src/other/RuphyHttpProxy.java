package other;//package me.muphy.servicce;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * http代理
 */
public class RuphyHttpProxy extends Thread {
    private final ServerSocket server;
    private final int port;

    public RuphyHttpProxy(int port) throws IOException {
        this.port = port;
        server = new ServerSocket(port);
        System.out.println("代理端口：" + this.port);
    }

    public static void main(String[] args) throws IOException {
        int port = 8888;
        if (args != null && args.length > 0 && args[0].matches("\\d+")) {
            port = Integer.parseInt(args[0]);
        }
        new RuphyHttpProxy(port).start();
    }

    @Override
    public void run() {
        // 线程运行函数
        while (true) {
            try {
                Socket client = server.accept();
                //使用线程处理收到的请求
                new HttpConnectThread(client).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 新连接处理线程
     */
    private static class HttpConnectThread extends Thread {

        private Socket client;
        private Socket server = null;
        private String host = null;
        private int port = 80;
        private int clientReadLength = 0;
        byte clientInputBuffer[] = new byte[1024 * 1024 * 4];
        private DataInputStream clientInputStream = null; //客户端输入流
        private DataInputStream serverInputStream = null; //服务端输入流
        private DataOutputStream clientOutputStream = null; //客户端输出流
        private DataOutputStream serverOutputStream = null;  //服务端输出流
        private long createTime = System.currentTimeMillis();
        private String clientInputString = null;

        public HttpConnectThread(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {

            try {
                clientInputStream = new DataInputStream(client.getInputStream());
                clientOutputStream = new DataOutputStream(client.getOutputStream());
                if (clientInputStream != null && clientOutputStream != null) {
                    clientReadLength = clientInputStream.read(clientInputBuffer, 0, clientInputBuffer.length); // 从客户端读数据
                    if (clientReadLength > 0) { // 读到数据
                        clientInputString = new String(clientInputBuffer, 0, clientReadLength);
                        if (clientInputString.contains("\n")) {
                            clientInputString = clientInputString.substring(0, clientInputString.indexOf("\n"));
                        }
                        if (clientInputString.contains("CONNECT ")) {
                            parseServerHost("CONNECT ([^ ]+) HTTP/");
                        } else if (clientInputString.contains("http://") && clientInputString.contains("HTTP/")) {
                            // 从所读数据中取域名和端口号
                            parseServerHost("http://([^/]+)/");
                        }
                        if (host != null) {
                            server = new Socket(host, port);
                            // 根据读到的域名和端口号建立套接字
                            serverInputStream = new DataInputStream(server.getInputStream());
                            serverOutputStream = new DataOutputStream(server.getOutputStream());
                            if (serverInputStream != null && serverOutputStream != null && server != null) {
                                if (clientInputString.contains("CONNECT ")) {
                                    doConnect();
                                    return;
                                }
                                doRequest();
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            IOUtils.close(serverInputStream, serverOutputStream, server, clientInputStream, clientOutputStream, client);
        }

        /**
         * 解析主机地址
         *
         * @param regExp
         */
        private void parseServerHost(String regExp) {
            Pattern pattern = Pattern.compile(regExp);
            Matcher matcher = pattern.matcher(clientInputString + "/");
            if (matcher.find()) {
                host = matcher.group(1);
                if (host.contains(":")) {
                    port = Integer.parseInt(host.substring(host.indexOf(":") + 1));
                    host = host.substring(0, host.indexOf(":"));
                }
            }
        }

        /**
         * 处理请求
         *
         * @throws IOException
         */
        private void doRequest() throws IOException, InterruptedException {
            serverOutputStream.write(clientInputBuffer, 0, clientReadLength);
            serverOutputStream.flush();
            final CountDownLatch latch;
            if (clientInputString.contains("POST ")) {
                latch = new CountDownLatch(2);
                // 建立线程 , 用于从内网读数据 , 并返回给外网
                new HttpChannel(clientInputStream, serverOutputStream, latch).start();
            } else {
                latch = new CountDownLatch(1);
            }
            // 建立线程 , 用于从外网读数据 , 并返回给内网
            new HttpChannel(serverInputStream, clientOutputStream, latch).start();
            latch.await(120, TimeUnit.SECONDS);
            IOUtils.close(serverInputStream, serverOutputStream, server, clientInputStream, clientOutputStream, client);
            System.out.println("请求地址：" + clientInputString + "，耗时：" + (System.currentTimeMillis() - createTime) + "ms");
        }

        /**
         * 处理连接请求
         *
         * @return
         */
        private void doConnect() throws IOException, InterruptedException {
            String ack = "HTTP/1.0 200 Connection established\r\n";
            ack = ack + "Proxy-agent: proxy\r\n\r\n";
            clientOutputStream.write(ack.getBytes());
            clientOutputStream.flush();
            final CountDownLatch latch = new CountDownLatch(2);
            // 建立线程 , 用于从外网读数据 , 并返回给内网
            new HttpChannel(serverInputStream, clientOutputStream, latch).start();
            // 建立线程 , 用于从内网读数据 , 并返回给外网
            new HttpChannel(clientInputStream, serverOutputStream, latch).start();
            latch.await(120, TimeUnit.SECONDS);
            IOUtils.close(serverInputStream, serverOutputStream, server, clientInputStream, clientOutputStream, client);
        }

    }

    /**
     * 流通道处理线程
     */
    private static class HttpChannel extends Thread {
        private final CountDownLatch countDownLatch;
        private final DataInputStream in;
        private final DataOutputStream out;

        public HttpChannel(DataInputStream in, DataOutputStream out, CountDownLatch countDownLatch) {
            this.in = in;
            this.out = out;
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void run() {
            int len;
            byte buf[] = new byte[10240];
            try {
                while ((len = in.read(buf, 0, buf.length)) != -1) {
                    out.write(buf, 0, len);
                    out.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                IOUtils.close(in, out);
                countDownLatch.countDown();
            }
        }
    }

    /**
     * 流工具类
     */
    private static class IOUtils {
        /**
         * 关闭所有流
         */
        private static void close(Closeable... closeables) {
            if (closeables != null) {
                for (int i = 0; i < closeables.length; i++) {
                    if (closeables[i] != null) {
                        try {
                            closeables[i].close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

}