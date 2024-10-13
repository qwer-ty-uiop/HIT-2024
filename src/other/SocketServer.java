package other;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author zyq
 * 简易的面向socket编程实现的HTTP代理。
 */
public class SocketServer {

    /**
     * 重定向主机map。
     */
    private static Map<String, String> redirectHostMap = new HashMap<>();

    /**
     * 重定向访问网址map。
     */
    private static Map<String, String> redirectAddrMap = new HashMap<>();

    /**
     * 禁止访问的网址。
     */
    private static Set<String> forbidSet = new HashSet<>();

    /**
     * 禁止访问的用户。
     */
    private static Set<String> forbidUser = new HashSet<>();

    static {
        // 更改这些内容达到屏蔽访问或钓鱼的目的

        // 这二者要配合使用
         redirectAddrMap.put("http://jwts.hit.edu.cn/loginLdapQian/", "http://today.hit.edu.cn/");
         redirectAddrMap.put("http://jwts.hit.edu.cn/loginLdapQian", "http://today.hit.edu.cn/");
         redirectHostMap.put("jwts.hit.edu.cn", "today.hit.edu.cn");

        forbidSet.add("http://jwes.hit.edu.cn/");
        forbidSet.add("http://jwes.hit.edu.cn");
        forbidSet.add("jwes.hit.edu.cn/");
        forbidSet.add("jwes.hit.edu.cn");

//        forbidUser.add("127.0.0.1");

    }

    /**
     * 判断site是否被禁止访问.
     *
     * @param site 网址/主机/访问资源
     * @return true表示禁止访问，否则为false
     */
    private static boolean isForbidden(String site) {
        return forbidSet.contains(site);
    }

    /**
     * 重定向主机.
     *
     * @param oriHost 原始主机
     * @return 根据redirectHostMap重定向后的主机
     */
    private static String redirectHost(String oriHost) {
        Set<String> keywordSet = redirectHostMap.keySet();
        for (String keyword : keywordSet) {
            if (oriHost.contains(keyword)) {
                System.out.println("originHost: " + oriHost);
                String redHost = redirectHostMap.get(oriHost);  // 直接修改方案
                System.out.println("redirectHost: " + redHost);
                return redHost;
            }
        }
        return oriHost;
    }

    /**
     * 重定向访问资源.
     * 通常是图片等
     *
     * @param oriAddr 想访问的资源
     * @return 根据redirectAddrMap重定向后的访问资源
     */
    private static String redirectAddr(String oriAddr) {
        Set<String> keywordSet = redirectAddrMap.keySet();
        for (String keyword : keywordSet) {
            if (oriAddr != null && oriAddr.contains(keyword)) {
                System.out.println("originAddr: " + oriAddr);
                String redAddr = redirectAddrMap.get(oriAddr);  // 直接修改方案
                System.out.println("redirectAddr: " + redAddr);
                return redAddr;
            }
        }
        return oriAddr;
    }

    /**
     * 通过header解析各个参数.
     *
     * @param header HTTP报文头部
     * @return map，包含HTTP版本，method，访问内容，主机，端口
     */
    private static Map<String, String> parse(String header) {
        if (header.length() == 0) {
            return new HashMap<>();
        }
        String[] lines = header.split("\\n");
        String method = null;
        String visitAddr = null;
        String httpVersion = null;
        String hostName = null;
        String portString = null;
        for (String line : lines) {
            if ((line.contains("GET") || line.contains("POST") || line.contains("CONNECT")) && method == null) {
                // 这一行包括get xxx httpVersion
                String[] temp = line.split("\\s");  // 按空格分割
                method = temp[0];
                visitAddr = temp[1];
                httpVersion = temp[2];
                // 对addr再获得端口号
                // 端口也在这里
                // 先判断是否包含http://关键字
                if (visitAddr.contains("http://") || visitAddr.contains("https://")) {
                    // 包含
                    // 再判断是否包含端口号
                    String[] temp1 = visitAddr.split(":");
                    // 因为有http://带来的冒号，所以如果长度>=3则有端口号
                    // 且temp[1]是host
                    if (temp1.length >= 3) {
                        portString = temp1[2];
                    }
                } else {
                    // 不包含http
                    String[] temp1 = visitAddr.split(":");
                    // 长度>=2则有端口号
                    if (temp1.length >= 2) {
                        // 有端口号，最后没有斜杠
                        portString = temp1[1];
                    }
                }

            } else if (line.contains("Host: ") && hostName == null) {
                String[] temp = line.split("\\s");
                hostName = temp[1];
                int maohaoIndex = hostName.indexOf(':');
                if (maohaoIndex != -1) {
                    hostName = hostName.substring(0, maohaoIndex);
                }
            }
        }

        Map<String, String> map = new HashMap<>();
        // 构造参数map
        map.put("method", method);
        map.put("visitAddr", visitAddr);
        map.put("httpVersion", httpVersion);
        map.put("host", hostName);
        if (portString == null) {
            map.put("port", "80");
        } else {
            map.put("port", portString);
        }
        return map;
    }


    public static void main(String[] args) throws IOException {
        // 监听指定的端口
        int port = 8888;
        ServerSocket server = new ServerSocket(port);
        // server将一直等待连接的到来
        System.out.println("server将一直等待连接的到来");

        // 如果使用多线程，那就需要线程池，防止并发过高时创建过多线程耗尽资源
        // 这是网上推荐的，但是本地访问量不大，没必要使用线程池
        ExecutorService threadPool = Executors.newFixedThreadPool(100);

        while (true) {
            Socket socket = server.accept();
            System.out.println("获取到一个连接！来自 " + socket.getInetAddress().getHostAddress());
            boolean pass = true;
            if (forbidUser.contains(socket.getInetAddress().getHostAddress())) {
                pass = false;
            }
            boolean finalPass = pass;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 解析header
                        InputStreamReader r = new InputStreamReader(socket.getInputStream());
                        BufferedReader br = new BufferedReader(r);
                        String readLine = br.readLine();
                        String host;

                        StringBuilder header = new StringBuilder();

                        while (readLine != null && !readLine.equals("")) {
                            header.append(readLine).append("\n");
                            readLine = br.readLine();
                        }

                        // 在输入流结束之后判断
                        // 判断用户是否被屏蔽
                        if (!finalPass) {
                            System.out.println("From a forbidden user.");
                            PrintWriter pw = new PrintWriter(socket.getOutputStream());
                            pw.println("You are a forbidden user!");
                            pw.close();

                            socket.close();
                            return;
                        }

                        // 打印参数表
                        Map<String, String> map = parse(header.toString());

                        System.out.println("-------------------");
                        System.out.println(map);
                        System.out.println("-------------------");

                        host = map.get("host"); // host
                        // 端口
                        String portString = map.getOrDefault("port", "80");
                        // 端口
                        int visitPort = Integer.parseInt(portString);
                        // 访问的网站
                        String visitAddr = map.get("visitAddr");
                        // method
                        String method = map.getOrDefault("method", "GET");

                        // 判断是否屏蔽掉这个网站
                        if (visitAddr != null && isForbidden(visitAddr)) {
                            // 被屏蔽，不允许访问
                            System.out.println("Visiting a forbidden site.");
                            PrintWriter pw = new PrintWriter(socket.getOutputStream());
                            pw.println("You can not visit " + visitAddr + "!");
                            pw.close();
                        } else {
                            // 获得跳转主机和资源
                            String tempRedAddr = redirectAddr(visitAddr);
                            if (!tempRedAddr.equals(visitAddr)) {
                                visitAddr = tempRedAddr;
                                host = redirectHost(host);
                            }

                            // 看看在不在缓存中
                            // 获得一下文件
                            File cacheFile = new File(visitAddr.replace('/', 'g') + ".mycache");
                            boolean useCache = false;   // 标记是否用cache

                            // 默认的最后修改时间，用于文件不存在的时候
                            String lastModified = "Thu, 01 Jul 1970 20:00:00 GMT";

                            if (cacheFile.exists() && cacheFile.length() != 0) {
                                // 文件存在且大小不为0，说明访问内容被缓存过
                                System.out.println(visitAddr + " 有缓存");
                                // 获得修改时间
                                Calendar cal = Calendar.getInstance();
                                long time = cacheFile.lastModified();
                                SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
                                cal.setTimeInMillis(time);
                                cal.set(Calendar.HOUR, -7);
                                cal.setTimeZone(TimeZone.getTimeZone("GMT"));
                                lastModified = formatter.format(cal.getTime());
                                System.out.println(cal.getTime());
                            }

                            // 已经知道时间了，发报文
                            // 向远程服务器发送请求报文，加入if modified since
                            // 创建新的socket连接远程服务器
                            Socket connectRemoteSocket = new Socket(host, visitPort);

                            // 这个是连接远程服务器的socket的stream
                            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connectRemoteSocket.getOutputStream()));

                            StringBuffer requestBuffer = new StringBuffer();
                            requestBuffer.append(method).append(" ").append(visitAddr)
                                    .append(" HTTP/1.1").append("\r\n")
                                    .append("HOST: ").append(host).append("\n")
                                    .append("Accept:text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\n")
                                    .append("Accept-Encoding:gzip, deflate, sdch\n")
                                    .append("Accept-Language:zh-CN,zh;q=0.8\n")
                                    .append("If-Modified-Since: ").append(lastModified).append("\n")
                                    .append("User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.2 Safari/605.1.15\n")
                                    .append("Encoding:UTF-8\n")
                                    .append("Connection:keep-alive" + "\n")
                                    .append("\n");
                            writer.write(requestBuffer.toString()); // 发送报文
                            // 打印看看
                            System.out.println(requestBuffer.toString());
                            // 发送报文
                            writer.flush();

                            // 这是向浏览器输出的输出流
                            OutputStream outToBrowser = socket.getOutputStream();

                            // 文件输出流
                            FileOutputStream fileOutputStream =
                                    new FileOutputStream(
                                            new File(visitAddr.replace('/', 'g') + ".mycache"));

                            // 从远程服务器获得输入的输入流
                            BufferedInputStream remoteInputStream =
                                    new BufferedInputStream(connectRemoteSocket.getInputStream());

                            // 先使用一个小字节缓存获得头部，包含第一行就够了
                            byte[] tempBytes = new byte[20];
                            int len = remoteInputStream.read(tempBytes);
                            String res = new String(tempBytes, 0, len);
                            System.out.println(res);
                            // 判断是否包含304，如果是包含，标记为使用缓存
                            if (res.contains("304")) {
                                // 远程服务器没有更新这个资源，可以直接使用缓存
                                System.out.println(visitAddr + " 服务器内容未变更，使用缓存");
                                // 刚才的小字节也不要了，后续的报文读完不用，然后直接从文件读
                                useCache = true;    // 用缓存
                            } else {
                                System.out.println(visitAddr + " 服务器内容可能变更，不使用缓存");

                                // 没有缓存，刚才临时读入的要用上。并且要接着读报文并向浏览器输出
                                outToBrowser.write(tempBytes);

                                // 临时字节写入缓存文件
                                fileOutputStream.write(tempBytes);
                            }
                            if (useCache) {
                                // 用缓存
                                // 这是向浏览器输出的输出流
                                System.out.println(visitAddr + " 正在使用缓存加载");
                                // 建立文件读写
                                FileInputStream fileInputStream = new FileInputStream(cacheFile);
                                int bufferLength = 1;
                                byte[] buffer = new byte[bufferLength];
                                int count;

                                while (true) {
                                    count = fileInputStream.read(buffer);
                                    System.out.println("Reading>.... From file>..." + count);
                                    if (count == -1) {
                                        break;
                                    }
                                    outToBrowser.write(buffer);
                                }
                                outToBrowser.flush();
                            }
                            // 不管用不用缓存都要接着读完来自服务器的数据
                            int bufferLength = 1;
                            byte[] buffer = new byte[bufferLength];
                            int count;
                            System.out.println("Start reading!>.....From > " + visitAddr);
                            while (true) {
                                count = remoteInputStream.read(buffer);
                                if (count == -1) {
                                    break;
                                }
                                if (!useCache) {
                                    // 不用缓存才写这些
                                    outToBrowser.write(buffer);
                                    fileOutputStream.write(buffer);
                                }
                            }
                            fileOutputStream.flush();   // 输出到文件
                            fileOutputStream.close();   // 关闭文件流
                            System.out.println("finish");

                            outToBrowser.flush();   // 输出到浏览器
                            connectRemoteSocket.close();    // 关闭连接远程服务器的socket

                        }
                        socket.close();// 关闭浏览器与程序的socket
                    } catch (IOException e) {
                        e.getMessage();
                    }
                }
            }).start();
        }
    }


}

