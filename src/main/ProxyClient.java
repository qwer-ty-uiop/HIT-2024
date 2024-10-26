package main;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class ProxyClient implements Runnable {

    private final Socket socket;    // 客户端与代理服务器连接的socket
    private String requestMethod;   // 客户端请求头的请求方法
    private URL url;                // 客户端请求访问的网址
    private String serverHost;      // 网址的 Host 部分
    private int serverPort;         // 访问网址的端口
    private String fileName;        // 用于存储浏览器缓存的文件名，用base64编码
    private static final Set<String> bannedGroups = new HashSet<>();    // 被 ban 的网页
    private static final Map<String, String> steerSite = new ConcurrentHashMap<>();     // 钓鱼网站映射
    private static final Map<String, String> lastModifiedMap = new ConcurrentHashMap<>();   // 存储网站的lastModified时间

    static {
        // 被禁止访问的网址
        bannedGroups.add("example.com");
        // 钓鱼网址
        steerSite.put("jwes.hit.edu.cn", "jwts.hit.edu.cn");
        // 初始化记录的 Last-Modified 的时间
        try (FileReader fileReader = new FileReader("src\\cache\\lastModified.txt")) {
            // 刚开始运行，还没有初始化（static的初始化一次就够了）
            if (lastModifiedMap.isEmpty()) {
                char[] line = new char[1024];
                while (fileReader.read(line) != -1) {
                    String[] splits = Arrays.toString(line).split("=");
                    lastModifiedMap.put(splits[0], splits[1]);
                }
            }
        } catch (IOException e) {
            System.out.println("还没有缓存文件" + e.getMessage());
        }
    }

    // 获取与代理服务器连接的 socket
    public ProxyClient(Socket clientSocket) {
        socket = clientSocket;
    }

    @Override
    public void run() {
        // 读取客户端请求
        try (BufferedReader inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            // 预处理请求
            processMessage(inFromClient);
            // 为了实验结果方便查看，手动禁止 microsoft.com 及其子网站(不然太慢了时间来不及)
            if (serverHost.contains("microsoft.com")) return;

            // 检查网址是否被禁
            if (bannedGroups.contains(serverHost)) {
                PrintWriter outToClient = new PrintWriter(socket.getOutputStream(), true);
                outToClient.println("<h1> 403 forbidden: This site has been banned <h1>");
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
//                throw new Exception(e);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                // 运行完毕，将更新的 lastModified 文件写入 cache，方便下次启动 server 的时候能够正常运行
                FileWriter fileWriter = new FileWriter("src\\cache\\lastModified.txt");
                for (Map.Entry<String, String> e : lastModifiedMap.entrySet())
                    fileWriter.write(e.getKey() + "=" + e.getValue() + "\n");
                socket.close();
                fileWriter.close();
            } catch (IOException e) {
                //
            }
        }
    }

    /**
     * 发送数据给客户端（浏览器）
     *
     * @param serverSocket 代理服务器连接服务器的socket
     */
    private void sendToClient(Socket serverSocket) throws IOException {
        BufferedInputStream inFromServer = new BufferedInputStream(serverSocket.getInputStream());
        OutputStream outToBrowser = socket.getOutputStream();

        byte[] response = new byte[1024];
        int len = inFromServer.read(response, 0, 12);
        String responseHead = new String(response, 0, len);
        // 打印 http 状态码信息出来看一下
        synchronized (ProxyClient.class) {
            System.out.println("url: " + url);
            System.out.println("\n========= start ===========");
            System.out.println(serverHost + " responseHead: \n" + responseHead);
            System.out.println("=========== end =========");
        }
        if (responseHead.contains("304")) {
            System.out.println(url + " 使用缓存，缓存命中");
            // 从缓存写入
            FileInputStream fis = new FileInputStream(fileName);
            while ((len = fis.read(response)) != -1) {
                outToBrowser.write(response, 0, len);
            }
            fis.close();
        } else {
            System.out.println(url + " 未使用缓存或还没有缓存");
            // 创建缓存文件
            FileOutputStream fos = new FileOutputStream(fileName);
            do {
                // 获取 Last-Modified 响应
                String responseData = new String(response, 0, len);
                if (responseData.contains("Last-Modified")) {
                    // 更新该网站的 Last-Modified
                    String[] splits = responseData.split("\r\n");
                    for (String line : splits) {
                        if (line.startsWith("Last-Modified:")) {
                            lastModifiedMap.put(serverHost, line.substring("Last-Modified:".length()));
                            break;
                        }
                    }
                }
                // 写入cache和浏览器
                outToBrowser.write(response, 0, len);
                fos.write(response, 0, len);
            } while ((len = inFromServer.read(response)) != -1);
        }
        outToBrowser.flush();
    }

    /**
     * 将客户端请求发送给服务端
     *
     * @param serverSocket 代理服务器连接服务器的socket
     */
    private void sendToServer(Socket serverSocket) throws IOException {
        BufferedWriter outToServer = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream()));
        // 获取缓存的lastModified时间
        String lastModified = lastModifiedMap.getOrDefault(serverHost, "Thu, 01 Jul 1990 20:00:00 GMT");
        // 还没有缓存就加上默认的时间
        if (!lastModifiedMap.containsKey(serverHost)) {
            lastModifiedMap.put(serverHost, lastModified);
            System.out.println(url + " 还没有缓存，缓存未命中");
        } else System.out.println(url + " 的缓存存在，最后修改时间为：" + lastModified);

        String request = requestMethod + " " + url + " HTTP/1.1" + "\r\n" + "HOST: " + url.getHost() + "\n" + "Accept:text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\n" + "Accept-Encoding:gzip, deflate, sdch\n" + "Accept-Language:zh-CN,zh;q=0.8\n" + "Cache-Control: max-age=600\n" + "If-Modified-Since: " + lastModified + "\n" + "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.2 Safari/605.1.15\n" + "Encoding:UTF-8\n" + "Connection:keep-alive" + "\n" + "\n";

        outToServer.write(request);
        outToServer.flush();
    }

    /**
     * 处理来自客户端的请求，获取基本信息和目标服务器地址
     *
     * @param inFromClient 来自客户端的请求
     */
    private void processMessage(BufferedReader inFromClient) throws IOException {
        // 先处理请求行
        String line = inFromClient.readLine();
        requestMethod = line.split(" ", 2)[0];
        String path = line.split(" ", 3)[1];
        if (path.contains("https")) path = path.replace("https", "http");
        if (!path.contains("http")) path = "http://" + path;
        url = new URL(path);
        // cache 文件名，用base64编码，防止出现违规文件命名
        fileName = "src\\cache\\" + Base64.getUrlEncoder().encodeToString(url.toString().getBytes()).substring(0, 20) + ".txt";
        serverHost = url.getHost();
        serverPort = url.getPort() == -1 ? 80 : url.getPort();
        System.out.println("serverHost: " + serverHost);
    }
}