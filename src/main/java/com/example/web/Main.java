package com.example.web;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<String> validPath = List.of("/index.html", "/spring.svg", "/spring.png",
                "/resources.html", "/styles.css", "/app.js",
                "/links.html", "/forms.html", "/classic.html",
                "/events.html", "/events.js", "/test.html");
        Server server = new Server(validPath, 64);

        // Пример 1: Обработка GET-запроса на /messages (Query Params)
        // http://localhost:9999/messages?id=5&user=Ivan
        server.addHandler("GET", "/messages", (request, out) -> {
            String id = request.getQueryParam("id").stream().findFirst().orElse("unknown");
            String user = request.getQueryParam("user").stream().findFirst().orElse("guest");

            String content = "<h1>Messages</h1><p>User: " + user + "</p><p>ID: " + id + "</p>";
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=UTF-8\r\n" +
                    "Content-Length: " + content.getBytes().length + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    content;
            out.write(response.getBytes());
            out.flush();
        });

        // Пример 2: Обработка POST на / (Данные формы из default-get.html)
        server.addHandler("POST", "/", (request, out) -> {
            String queryVal = request.getQueryParam("value").stream().findFirst().orElse("none");
            String title = request.getPostParam("title").stream().findFirst().orElse("без заголовка");
            List<String> values = request.getPostParam("value");

            System.out.println("value: " + queryVal);
            System.out.println("title: " + title);
            System.out.println("value list: " + values);

            String content = "POST data received! Title: " + title;
            String response = "HTTP/1.1 201 Created\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Length: " + content.getBytes().length + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    content;
            out.write(response.getBytes());
            out.flush();
        });

        // Пример 3: POST на /messages
        server.addHandler("POST", "/messages", (request, out) -> {
            String body = new String(request.getBody(), StandardCharsets.UTF_8);
            System.out.println("Получено POST-сообщение (raw body): " + body);

            String content = "Сообщение успешно получено сервером!";
            String response = "HTTP/1.1 201 Created\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Length: " + content.getBytes().length + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    content;
            out.write(response.getBytes());
            out.flush();
        });

        System.out.println("Server starting on port 9999...");

        server.startServer(9999);
    }
}