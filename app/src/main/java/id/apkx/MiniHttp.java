package id.apkx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public class MiniHttp extends Thread {
    private final ServerSocket server;
    private final Function<String, String> handler;
    private volatile boolean alive = true;

    public MiniHttp(int port, Function<String, String> handler) throws IOException {
        super("apkx-http");
        this.server = new ServerSocket();
        this.server.setReuseAddress(true);
        this.server.bind(new InetSocketAddress("127.0.0.1", port));
        this.handler = handler;
    }

    public void stopServer() {
        alive = false;
        try { server.close(); } catch (IOException ignored) {}
    }

    @Override public void run() {
        while (alive) {
            try (Socket s = server.accept()) {
                s.setSoTimeout(180000);
                serve(s);
            } catch (Exception e) {
                if (alive) ApkxService.log("HTTP: " + e);
            }
        }
    }

    private void serve(Socket s) throws IOException {
        InputStream in = s.getInputStream();
        String line = readLine(in);
        if (line == null) return;
        String[] parts = line.split(" ");
        String method = parts.length > 0 ? parts[0] : "GET";
        String path = parts.length > 1 ? parts[1] : "/";
        int len = 0;
        String h;
        while ((h = readLine(in)) != null && !h.isEmpty()) {
            if (h.toLowerCase().startsWith("content-length:")) {
                len = Integer.parseInt(h.substring(15).trim());
            }
        }
        byte[] body = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(body, off, len - off);
            if (n < 0) break;
            off += n;
        }

        int code = 200;
        String resp;
        if (path.startsWith("/mcp") && method.equals("POST")) {
            resp = handler.apply(new String(body, StandardCharsets.UTF_8));
            if (resp == null || resp.isEmpty()) { code = 202; resp = ""; }
        } else if (path.startsWith("/mcp") && method.equals("GET")) {
            code = 204;
            resp = "";
        } else {
            resp = "{\"name\":\"apkx\",\"endpoint\":\"/mcp\"}";
        }

        byte[] out = resp.getBytes(StandardCharsets.UTF_8);
        String reason = code == 200 ? "OK" : code == 202 ? "Accepted" : code == 204 ? "No Content" : "OK";
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        head.append("Content-Type: application/json\r\n");
        head.append("Content-Length: ").append(out.length).append("\r\n");
        head.append("Access-Control-Allow-Origin: *\r\n");
        head.append("Connection: close\r\n\r\n");
        OutputStream os = s.getOutputStream();
        os.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        os.write(out);
        os.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
        }
        if (c == -1 && b.size() == 0) return null;
        return b.toString("ISO-8859-1");
    }
}
