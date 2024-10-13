package other;

import java.io.*;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class ProxyServer {

    // 代理服务器监听的端口
    private static final int PROXY_PORT = 8888;
    private static final Set<String> bannedUsers = new HashSet<>();
    private static final Set<String> bannedGroups = new HashSet<>();
    private static final Map<String, String> steerSite = new ConcurrentHashMap<>();

    static {
        steerSite.put("today.hit.edu.cn", "jwes.hit.edu.cn");

        bannedGroups.add("example.com");
        bannedGroups.add("microsoft.com");

//        bannedUsers.add("127.0.0.1");
    }

    public static void main(String[] args) {
        // 创建socket
        try (ServerSocket proxySocket = new ServerSocket(PROXY_PORT)) {
            System.out.println("代理服务器启动，正在监听端口：" + PROXY_PORT);
            // 创建线程池
            try (ThreadPoolExecutor pool = new ThreadPoolExecutor(3, 9, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(2), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy())) {
                // 处理客户端连接
                while (true) {
                    // 暂无空闲线程，循环等待线程空出
                    if (pool.getActiveCount() >= 9) continue;

                    // 接受客户端连接
                    Socket clientSocket = proxySocket.accept();
                    System.out.println("客户端连接：" + clientSocket.getInetAddress());

                    // 查看用户是否被封禁
                    if (bannedUsers.contains(clientSocket.getInetAddress().getHostAddress())) {
                        PrintWriter response = new PrintWriter(clientSocket.getOutputStream(), true);
                        response.println("You are banned from access");
                        System.out.println("该用户被禁止访问: " + clientSocket.getInetAddress().getHostAddress());
                        response.close();
                        continue;
                    }

                    // 处理客户端的请求
                    pool.submit(new HandleThread(clientSocket));
                }
            } catch (Exception e) {
                System.out.println("线程池创建失败: " + e.getMessage());
            }
        } catch (IOException e) {
            System.out.println("代理服务器错误: " + e.getMessage());
        }
    }

    /**
     * 内部类，用于处理客户端请求
     */
    public static class HandleThread implements Runnable {
        final Socket clientSocket;
        String requestMethod, url, lastModified, targetHost;
        URL targetUrl;
        int targetPort;

        public HandleThread(Socket clientSocket) {
            // 获取客户端socket
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            // 从客户端读取请求
            try (BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                // 读取客户端的 HTTP 请求的第一行，即请求行
                String requestLine = clientReader.readLine();
                if (requestLine == null || requestLine.isEmpty()) return;
                System.out.println(Thread.currentThread().getName() + "客户端请求行: " + requestLine);

                // 解析请求行中的 URL，提取目标服务器地址
                getServerAddress(requestLine);

                // 处理一下targetHost
                String[] splitsHost = targetHost.split("\\.");
                String mainHost = splitsHost[splitsHost.length - 2] + "." + splitsHost[splitsHost.length - 1];
                // 如果目标服务器地址被禁，则直接返回
                if (bannedGroups.contains(mainHost)) {
                    PrintWriter response = new PrintWriter(clientSocket.getOutputStream(), true);
                    response.println("This site has been banned");
                    System.out.println("\n ---------- 这个网址被禁止了: " + mainHost + " ---------- \n");
                    response.close();
                    return;
                }

                // 如果目标服务器是钓鱼源地址，那么转换其为钓鱼目的地址
                if (steerSite.containsKey(targetHost)) targetHost = steerSite.get(targetHost);

                // 创建与目标服务器的连接
                try (Socket serverSocket = new Socket(targetHost, targetPort)) {
                    // 创建传输通道
                    BufferedWriter serverWriter = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream()));
                    BufferedInputStream serverReader = new BufferedInputStream(serverSocket.getInputStream());

                    // 将客户端请求转发给目标服务器
                    sendToServer(serverWriter);
                    // 读取目标服务器的响应并转发给客户端
                    printToBrowser(serverReader);

                    serverWriter.close();
                    serverReader.close();

                    System.out.println(Thread.currentThread().getName() + "网址: " + url + "响应成功！");


                } catch (IOException e) {
                    System.out.println(Thread.currentThread().getName() + "目标服务器连接失败: " + e.getMessage());
                }
            } catch (IOException e) {
                System.out.println(Thread.currentThread().getName() + "处理客户端请求失败: " + e.getMessage());
            } finally {
                // 关闭连接
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.out.println(Thread.currentThread().getName() + "关闭客户端套接字失败: " + e.getMessage());
                }
            }
        }

        /**
         * 获取客户端请求的服务端地址
         *
         * @param requestLine 客户端请求行
         * @throws MalformedURLException 创建的url可能不存在，或者url错误
         */
        private void getServerAddress(String requestLine) throws MalformedURLException {
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) return;

            requestMethod = requestParts[0];
            url = requestParts[1];
            // 默认的最后修改时间，用于文件不存在的时候
            lastModified = "Thu, 01 Jul 1970 20:00:00 GMT";
            if (url.startsWith("https://")) url = url.replaceFirst("https://", "http://");
            else if (!url.startsWith("http://")) url = "http://" + url;

            // 从 URL 中提取目标服务器的主机名和端口号
            targetUrl = new URL(url);
            targetHost = targetUrl.getHost();
            targetPort = targetUrl.getPort() == -1 ? 80 : targetUrl.getPort();  // 默认端口为80

            System.out.println(Thread.currentThread().getName() + "目标服务器地址: " + targetHost + ":" + targetPort);

        }

        /**
         * 将客户端的报文发送给服务端
         *
         * @param serverWriter 向服务器写数据的通道
         */
        private void sendToServer(BufferedWriter serverWriter) throws IOException {
            File cacheFile = new File("src\\cache\\" + url.replace("/", "$").substring(7) + ".txt");

            if (cacheFile.exists() && cacheFile.length() != 0) {
                SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
                format.setTimeZone(TimeZone.getTimeZone("GMT"));
                lastModified = format.format(new Date(cacheFile.lastModified()));
                System.out.println(targetUrl.toString() + " 缓存存在,，最后修改时间" + lastModified);
            } else System.out.println(targetUrl.toString() + " 无缓存");

            String request = requestMethod + " " + url + " HTTP/1.1" + "\r\n" + "HOST: " + targetHost + "\n" + "Accept:text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\n" + "Accept-Encoding:gzip, deflate, sdch\n" + "Accept-Language:zh-CN,zh;q=0.8\n" + "If-Modified-Since: " + lastModified + "\n" + "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.2 Safari/605.1.15\n" + "Encoding:UTF-8\n" + "Connection:keep-alive" + "\n" + "\n";
            serverWriter.write(request);
            serverWriter.flush();
        }

        /**
         * 将服务端的数据发送给客户端
         *
         * @param serverReader 从服务器读数据的通道
         */
        private void printToBrowser(BufferedInputStream serverReader) throws IOException {
            OutputStream outToBrowser = clientSocket.getOutputStream();

            byte[] response = new byte[1024];
            int len = serverReader.read(response, 0, 32);
            String res = new String(response, 0, len);

            System.out.println("\n=======================================");
            System.out.print(res);
            System.out.println(lastModified);
            System.out.println("=======================================\n");

//            File cacheFile = new File("src\\cache\\" + url.replace("/", "$").replace(".", "&").substring(7, 14));

            if (res.contains("304")) { // 状态码304：可以用缓存
                // if (cacheFile.exists() && cacheFile.length() != 0) { // 或者有缓存就直接用
                System.out.println(Thread.currentThread().getName() + "资源未被修改，使用缓存");
                FileInputStream fileInputStream = new FileInputStream("src\\cache\\" + url.replace("/", "$").substring(7) + ".txt");
                while ((len = fileInputStream.read(response)) != -1) {
                    outToBrowser.write(response, 0, len);
                }
                fileInputStream.close();
            } else {
                System.out.println(Thread.currentThread().getName() + " lastModified不匹配，不使用缓存 " + targetUrl);
                FileOutputStream fileOutputStream = new FileOutputStream("src\\cache\\" + url.replace("/", "$").substring(7) + ".txt");
                do {
                    outToBrowser.write(response, 0, len);
                    fileOutputStream.write(response, 0, len);
                } while ((len = serverReader.read(response)) != -1);
                fileOutputStream.close();
            }
            outToBrowser.flush();
        }
    }
}
