package com.example.web;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static String GET = "GET";
    private static String POST = "POST";
    private final List<String> validPaths;
    private final ExecutorService threadPool;
    private final Map<String, Map<String, Handler>> handlers = new ConcurrentHashMap<>();
    private final String publicFolder = "public";

    public Server(List<String> validPaths, int poolSize) {
        this.validPaths = validPaths;
        this.threadPool = Executors.newFixedThreadPool(poolSize);
    }

    public void addHandler(String method, String path, Handler handler) {
        handlers.putIfAbsent(method, new ConcurrentHashMap<>());
        handlers.get(method).put(path, handler);
    }

    public void startServer(int port) {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                try {
                    final var socket = serverSocket.accept();
                    threadPool.submit(() -> processConnection(socket));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void processConnection(Socket socket) {
        final var allowedMethods = List.of(GET, POST);
        try (
                socket;
                final var in = new BufferedInputStream(socket.getInputStream());
                final var out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            final var limit = 4096;

            in.mark(limit);
            final var buffer = new byte[limit];
            final var read = in.read(buffer);

            final var requestLineDelimiter = new byte[]{'\r', '\n'};
            final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                sendErrorHeader(out, "404 Not Found");
                return;
            }

            final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (requestLine.length != 3) {
                return;
            }

            final var method = requestLine[0];
            if (!allowedMethods.contains(method)) {
                sendErrorHeader(out, "405 Method Not Allowed");
                return;
            }
            System.out.println("method: " + method);

            final var path = requestLine[1];
            String pathTrue;
            String queryString = null;

            int queryStringIndex = path.indexOf('?');
            if (queryStringIndex != -1) {
                pathTrue = path.substring(0, queryStringIndex);
                queryString = path.substring(queryStringIndex + 1);
            } else {
                pathTrue = path;
            }
            if (!pathTrue.startsWith("/")) {
                sendErrorHeader(out, "404 Not Found");
                return;
            }
            System.out.println("path: " + path);
            System.out.println("pathTrue = " + pathTrue);
            System.out.println("queryString = " + queryString);

            final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final var headersStart = requestLineEnd + requestLineDelimiter.length;
            final var headerEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headerEnd == -1) {
                sendErrorHeader(out, "404 Not Found");
                return;
            }

            in.reset();
            in.skip(headersStart);

            final var headerBytes = in.readNBytes(headerEnd - headersStart);
            final var headerLines = Arrays.asList(new String(headerBytes).split("\r\n"));
            Map<String, String> headersMap = parseHeaders(headerLines);
            System.out.println("headers: " + headersMap);

            byte[] body = new byte[0];
            if (!method.equals(GET)) {
                in.skip(headersDelimiter.length);
                final var contentLength = extractHeader(headerLines, "Content-Length");
                if (contentLength.isPresent()) {
                    final var length = Integer.parseInt(contentLength.get());
                    body = in.readNBytes(length);
                    System.out.println("body: " + Arrays.toString(body));
                }
            }

            final var queryParams = URLEncodedUtils.parse(queryString != null ? queryString : "", StandardCharsets.UTF_8);

            List<NameValuePair> postParams = new ArrayList<>();
            if (method.equals("POST")) {
                var contentType = headersMap.get("Content-Type");
                if (contentType != null && contentType.equals("application/x-www-form-urlencoded")) {
                    String bodyString = new String(body, StandardCharsets.UTF_8);
                    postParams = URLEncodedUtils.parse(bodyString, StandardCharsets.UTF_8);
                }
            }

            final var request = new Request(method, pathTrue, headersMap, body, queryParams, postParams);

            if (tryHandleCustom(request, out)) {
                return;
            }

            if (!validPaths.contains(pathTrue)) {
                sendErrorHeader(out, "404 Not Found");
                return;
            }

            handleFile(pathTrue, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean tryHandleCustom(Request request, BufferedOutputStream out) throws IOException {
        var methodHandlers = handlers.get(request.getMethod());
        if (methodHandlers != null) {
            var handler = methodHandlers.get(request.getPath());
            if (handler != null) {
                handler.handle(request, out);
                return true;
            }
        }
        return false;
    }

    public void handleFile(String path, BufferedOutputStream out) throws IOException {
        final var filePath = Path.of(publicFolder, path);
        final var mimeType = Files.probeContentType(filePath);
        if (path.equals("/classic.html")) {
            classicHeader(out, filePath, mimeType);
        } else {
            final var length = Files.size(filePath);
            sendOkHeader(out, mimeType, length);
            Files.copy(filePath, out);
            out.flush();
        }
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.toLowerCase().startsWith(header.toLowerCase() + ":")) 
                .map(o -> o.substring(o.indexOf(":") + 1)) 
                .map(String::trim)
                .findFirst();
    }

    public void sendOkHeader(BufferedOutputStream out, String mimeType, long length) throws IOException {
        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
    }

    public void sendErrorHeader(BufferedOutputStream out, String status) throws IOException {
        out.write((
                "HTTP/1.1 " + status + "\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    public void classicHeader(BufferedOutputStream out, Path filePath, String mimeType) throws IOException {
        final var template = Files.readString(filePath);
        final var content = template.replace(
                "{time}",
                LocalDateTime.now().toString()
        ).getBytes();
        sendOkHeader(out, mimeType, content.length);
        out.write(content);
        out.flush();
    }

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private Map<String, String> parseHeaders(List<String> headerLines) {
        Map<String, String> result = new HashMap<>();
        for (String line : headerLines) {
            int i = line.indexOf(":");
            if (i != -1) {
                result.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
            }
        }
        return result;
    }
}
