package main;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class ProxyClient implements Runnable {
    private Socket socket;
    private String requestMethod;
    private URL url;
    private String serverHost;
    private int serverPort;
    private String fileName;
    private static final Set<String> bannedGroups = new HashSet<>();
    private static final Map<String, String> steerSite = new ConcurrentHashMap<>();

    {
        // 被禁止访问的网址
        bannedGroups.add("example.com");
        bannedGroups.add("microsoft.com");
        // 钓鱼网址
        steerSite.put("jwes.hit.edu.cn", "today.hit.edu.cn");
    }

    public ProxyClient(Socket clientSocket) {
        socket = clientSocket;
    }

    @Override
    public void run() {
        // 读取客户端请求
        try (BufferedReader inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            // 预处理请求
            processMessage(inFromClient);

            // 检查网址是否被禁，只看权威域名（二级域名）和顶级域名（一级域名）
            if (bannedGroups.contains(serverHost.substring(serverHost.lastIndexOf(".", serverHost.lastIndexOf(".") - 1) + 1))) {
                PrintWriter outToClient = new PrintWriter(socket.getOutputStream(), true);
                outToClient.println("<h1> 403 forbidden: 该网站被禁止访问 <h1>");
                System.out.println("该网站被禁止访问" + serverHost);
                return;
            }

            // 检查是否是要钓鱼的网站，如果是，则将目标host转换成钓鱼网站的host
            if (steerSite.containsKey(serverHost)) serverHost = steerSite.get(serverHost);

            // 创建与目标服务器的连接
            try (Socket serverSocket = new Socket(serverHost, serverPort)) {
                sendToServer(serverSocket);
                sendToClient(serverSocket);
                System.out.println(url + " 响应成功！");
            } catch (IOException e) {
                throw new Exception(e);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void sendToClient(Socket serverSocket) throws IOException {
        BufferedInputStream inFromServer = new BufferedInputStream(serverSocket.getInputStream());
        OutputStream outToBrowser = socket.getOutputStream();

        byte[] response = new byte[1024];
        int len = inFromServer.read(response, 0, 20);
        String responseHead = new String(response, 0, len);

        synchronized (ProxyClient.class) {
            System.out.println("url: " + url);
            System.out.println("\n========= start ===========");
            System.out.println("responseHead" + responseHead);
            System.out.println("=========== end =========");
        }
        if (responseHead.contains("304")) {
            System.out.println(url + " 使用缓存");
            // 从缓存写入
            FileInputStream fis = new FileInputStream(fileName);
            while ((len = fis.read(response)) != -1) {
                outToBrowser.write(response, 0, len);
            }
            fis.close();
        } else {
            System.out.println(url + " 未使用缓存或还没有缓存");
            // 创建缓存
            FileOutputStream fos = new FileOutputStream(fileName);
            do {
                outToBrowser.write(response, 0, len);
                fos.write(response, 0, len);
            } while ((len = inFromServer.read(response)) != -1);

        }
        outToBrowser.flush();
    }

    private void sendToServer(Socket serverSocket) throws IOException {
        BufferedWriter outToServer = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream()));
        File cacheFile = new File(fileName);
        String lastModified = "Thu, 01 Jul 1970 20:00:00 GMT";
        if (cacheFile.exists() && cacheFile.length() != 0) {
            SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
            formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
            lastModified = formatter.format(new Date(cacheFile.lastModified()));
            System.out.println(url + " 的缓存存在，最后修改时间为：" + lastModified);
        } else System.out.println(url + "还没有缓存");

        String request = requestMethod + " " + url + " HTTP/1.1" + "\r\n" + "HOST: " + url.getHost() + "\n" + "Accept:text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\n" + "Accept-Encoding:gzip, deflate, sdch\n" + "Accept-Language:zh-CN,zh;q=0.8\n" + "If-Modified-Since: " + lastModified + "\n" + "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.2 Safari/605.1.15\n" + "Encoding:UTF-8\n" + "Connection:keep-alive" + "\n" + "\n";

        outToServer.write(request);
        outToServer.flush();
    }

    private void processMessage(BufferedReader inFromClient) throws IOException {
        // 先处理请求行
        String line = inFromClient.readLine();
        requestMethod = line.split(" ", 2)[0];
        String path = line.split(" ", 3)[1];
        if (path.contains("https")) path = path.replace("https", "http");
        if (!path.contains("http")) path = "http://" + path;
        url = new URL(path);
        fileName = "src\\cache\\" + Base64.getUrlEncoder().encodeToString(url.toString().getBytes()).substring(0, 20) + ".txt";
        serverHost = url.getHost();
        serverPort = url.getPort() == -1 ? 80 : url.getPort();
        System.out.println("serverHost: " + serverHost);
    }
}
