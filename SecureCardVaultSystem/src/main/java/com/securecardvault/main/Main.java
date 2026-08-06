package com.securecardvault.main;

import com.securecardvault.model.Card;
import com.securecardvault.service.CardService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final CardService cardService = new CardService();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/saveCard", Main::handleSaveCard);
        server.createContext("/payment", Main::handlePayment);
        server.createContext("/transactions", Main::handleTransactions);
        server.createContext("/deleteToken", Main::handleDeleteToken);
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on http://localhost:8080");
    }

    private static void handleSaveCard(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) {
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
            return;
        }

        try {
            Map<String, String> body = readJsonBody(exchange);
            Card card = new Card();
            card.setCardHolderName(body.getOrDefault("holderName", body.getOrDefault("cardHolderName", body.getOrDefault("cardholder_name", ""))));
            card.setCardNumber(body.getOrDefault("cardNumber", body.getOrDefault("card_number", "")));
            card.setExpiryDate(body.getOrDefault("expiryDate", body.getOrDefault("expiry_date", "")));
            card.setCvv(body.getOrDefault("cvv", ""));

            Map<String, Object> response = cardService.saveCard(card);
            sendJson(exchange, 200, toJson(response));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, toJson(Map.of("success", false, "message", e.getMessage())));
        } catch (Exception e) {
            sendJson(exchange, 500, toJson(Map.of("success", false, "message", e.getMessage() != null ? e.getMessage() : "Unable to save card")));
        }
    }

    private static void handlePayment(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) {
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
            return;
        }

        try {
            Map<String, String> body = readJsonBody(exchange);
            String token = body.getOrDefault("token", "");
            double amount = Double.parseDouble(body.getOrDefault("amount", "0"));
            Map<String, Object> response = cardService.makePayment(token, amount);
            sendJson(exchange, 200, toJson(response));
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, toJson(Map.of("success", false, "message", "Invalid amount")));
        } catch (Exception e) {
            sendJson(exchange, 500, toJson(Map.of("success", false, "message", "Unable to process payment")));
        }
    }

    private static void handleTransactions(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) {
            return;
        }

        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
            return;
        }

        try {
            Map<String, Object> response = cardService.getTransactionHistory();
            sendJson(exchange, 200, toJson(response));
        } catch (Exception e) {
            sendJson(exchange, 500, toJson(Map.of("success", false, "message", "Unable to load transactions")));
        }
    }

    private static void handleDeleteToken(HttpExchange exchange) throws IOException {
        if (handleOptions(exchange)) {
            return;
        }

        if (!"DELETE".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
            return;
        }

        try {
            Map<String, String> body = readJsonBody(exchange);
            String token = body.getOrDefault("token", "");
            Map<String, Object> response = cardService.deleteToken(token);
            sendJson(exchange, 200, toJson(response));
        } catch (Exception e) {
            sendJson(exchange, 500, toJson(Map.of("success", false, "message", "Unable to delete token")));
        }
    }

    private static boolean handleOptions(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 200, "{}");
            return true;
        }
        return false;
    }

    private static Map<String, String> readJsonBody(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> values = new LinkedHashMap<>();

        if (requestBody == null || requestBody.isBlank()) {
            return values;
        }

        String trimmedBody = requestBody.trim();
        if (!trimmedBody.startsWith("{") || !trimmedBody.endsWith("}")) {
            return values;
        }

        Pattern pattern = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*(\\\"([^\\\"]*)\\\"|([^,}]+))");
        Matcher matcher = pattern.matcher(trimmedBody);
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String rawValue = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            String value = rawValue == null ? "" : rawValue.trim();
            values.put(key, value);
        }

        return values;
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.startsWith("http://localhost") || origin.startsWith("http://127.0.0.1")) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin == null ? "*" : origin);
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }

    private static String toJson(Map<String, Object> response) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : response.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append('"').append(entry.getKey()).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append('"').append(escapeJson((String) value)).append('"');
            } else if (value instanceof Boolean) {
                json.append(value);
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value instanceof Map) {
                json.append(mapToJson((Map<?, ?>) value));
            } else if (value instanceof Iterable) {
                json.append(listToJson((Iterable<?>) value));
            } else {
                json.append('"').append(escapeJson(String.valueOf(value))).append('"');
            }
        }
        json.append("}");
        return json.toString();
    }

    private static String mapToJson(Map<?, ?> map) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append('"').append(entry.getKey()).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append('"').append(escapeJson((String) value)).append('"');
            } else if (value instanceof Boolean) {
                json.append(value);
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value instanceof Map) {
                json.append(mapToJson((Map<?, ?>) value));
            } else if (value instanceof Iterable) {
                json.append(listToJson((Iterable<?>) value));
            } else {
                json.append('"').append(escapeJson(String.valueOf(value))).append('"');
            }
        }
        json.append("}");
        return json.toString();
    }

    private static String listToJson(Iterable<?> values) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                json.append(",");
            }
            first = false;
            if (value instanceof String) {
                json.append('"').append(escapeJson((String) value)).append('"');
            } else if (value instanceof Boolean) {
                json.append(value);
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value instanceof Map) {
                json.append(mapToJson((Map<?, ?>) value));
            } else if (value instanceof Iterable) {
                json.append(listToJson((Iterable<?>) value));
            } else {
                json.append('"').append(escapeJson(String.valueOf(value))).append('"');
            }
        }
        json.append("]");
        return json.toString();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
