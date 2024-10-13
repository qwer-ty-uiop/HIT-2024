package other;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class ProxyThread {
    public static void main(String[] args) throws IOException {
        System.out.println("是否启用过滤IP? [Y/n]");
        String scan = new Scanner(System.in).nextLine();
        boolean b = "y".equalsIgnoreCase(scan);

        ServerSocket ss = null;
        try {
            ss = new ServerSocket(8080);
        } catch (IOException e) {
            e.getMessage();
        }

        while (true) {
            try {
                Socket s = ss.accept();
                OutputStream os = s.getOutputStream();
                InputStream is = s.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                s.setSoTimeout(60 * 1000);
                String type = "", url = "", host = "", endAddress = "", line = "";
                int port = 80, lineNum = 0;

                while (((line = br.readLine()) != null)) {
                    System.out.println(line);
                    String[] splitLine = line.split(" ");
                    if (lineNum == 0) {
                        type = splitLine[0];
                        url = splitLine[1];
                    }
                    if (type == null || type.isEmpty()) continue;
                    if(line.isEmpty())break;
                }
            } catch (IOException e) {

            }
        }

    }
}
