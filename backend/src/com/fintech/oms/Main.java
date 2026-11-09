package com.fintech.oms;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * KRONOS Commerce - Order & Inventory Backend
 * Port 8080 - Order & Inventory Management System (Normal Email/Password Auth)
 */
public class Main {
    private static final int PORT = 8080;
    private static final String DATA_FILE = "data/oms_db.json";

    private static final List<Product> products = new CopyOnWriteArrayList<>();
    private static final List<Order> orders = new CopyOnWriteArrayList<>();
    private static final List<User> users = new CopyOnWriteArrayList<>();
    private static final Map<String, User> sessions = new ConcurrentHashMap<>();

    private static final AtomicInteger productIdSeq = new AtomicInteger(100);
    private static final AtomicInteger orderIdSeq = new AtomicInteger(1000);
    private static final AtomicInteger userIdSeq = new AtomicInteger(10);

    public static void main(String[] args) throws IOException {
        loadData();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // 1. AUTHENTICATION (Normal Login & Sign Up)
        server.createContext("/api/auth", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                addCors(ex);
                String path = ex.getRequestURI().getPath();
                String method = ex.getRequestMethod().toUpperCase();

                if ("OPTIONS".equals(method)) {
                    ex.sendResponseHeaders(204, -1);
                    return;
                }

                // 1A. Normal Sign Up
                if ("/api/auth/signup".equals(path) && "POST".equals(method)) {
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String name = extractField(body, "name");
                    String email = extractField(body, "email");
                    String password = extractField(body, "password");

                    if (name.isEmpty() || email.isEmpty() || password.length() < 4) {
                        sendJson(ex, 400, "{\"error\":\"Please enter a valid name, email, and password (min 4 characters).\"}");
                        return;
                    }

                    synchronized (users) {
                        for (User u : users) {
                            if (u.getEmail().equalsIgnoreCase(email)) {
                                sendJson(ex, 400, "{\"error\":\"An account with this email already exists.\"}");
                                return;
                            }
                        }

                        String hashed = hashPassword(password);
                        User newUser = new User(userIdSeq.incrementAndGet(), name, email, hashed, "Store Manager", LocalDate.now().toString());
                        users.add(newUser);
                        saveData();

                        String token = UUID.randomUUID().toString();
                        sessions.put(token, newUser);
                        sendJson(ex, 201, userAuthJson(token, newUser));
                    }
                    return;
                }

                // 1B. Normal Login (Email & Password)
                if ("/api/auth/login".equals(path) && "POST".equals(method)) {
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String email = extractField(body, "email");
                    String password = extractField(body, "password");

                    String hashed = hashPassword(password);
                    User found = null;
                    for (User u : users) {
                        if (u.getEmail().equalsIgnoreCase(email)) {
                            if (u.getPasswordHash() != null && u.getPasswordHash().equalsIgnoreCase(hashed)) {
                                found = u;
                                break;
                            }
                        }
                    }

                    if (found != null) {
                        String token = UUID.randomUUID().toString();
                        sessions.put(token, found);
                        sendJson(ex, 200, userAuthJson(token, found));
                    } else {
                        sendJson(ex, 401, "{\"error\":\"Invalid email or password.\"}");
                    }
                    return;
                }

                // 1C. Profile Check
                if ("/api/auth/me".equals(path) && "GET".equals(method)) {
                    User user = getAuthenticatedUser(ex);
                    if (user != null) {
                        String json = String.format(Locale.US,
                                "{\"authenticated\":true,\"user\":{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"role\":\"%s\",\"createdAt\":\"%s\"}}",
                                user.getId(), escape(user.getName()), escape(user.getEmail()), escape(user.getRole()), escape(user.getCreatedAt())
                        );
                        sendJson(ex, 200, json);
                    } else {
                        sendJson(ex, 401, "{\"authenticated\":false}");
                    }
                    return;
                }

                // 1D. Sign Out
                if ("/api/auth/logout".equals(path) && "POST".equals(method)) {
                    String authHeader = ex.getRequestHeaders().getFirst("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        sessions.remove(authHeader.substring(7).trim());
                    }
                    sendJson(ex, 200, "{\"success\":true}");
                    return;
                }

                sendJson(ex, 404, "{\"error\":\"Route not found\"}");
            }
        });

        // 2. SUMMARY METRICS
        server.createContext("/api/summary", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                addCors(ex);
                if ("OPTIONS".equals(ex.getRequestMethod())) {
                    ex.sendResponseHeaders(204, -1);
                    return;
                }

                BigDecimal totalRevenue = BigDecimal.ZERO;
                int totalOrders = 0;

                for (Order o : orders) {
                    if (!"CANCELLED".equalsIgnoreCase(o.getStatus())) {
                        totalRevenue = totalRevenue.add(o.getTotalAmount());
                    }
                    totalOrders++;
                }

                int totalProducts = products.size();
                int lowStockCount = 0;
                for (Product p : products) {
                    if (p.getStock() <= 5) lowStockCount++;
                }

                String json = String.format(Locale.US,
                        "{\"totalRevenue\":%.2f,\"totalOrders\":%d,\"totalProducts\":%d,\"lowStockCount\":%d}",
                        totalRevenue.setScale(2, RoundingMode.HALF_UP).doubleValue(),
                        totalOrders,
                        totalProducts,
                        lowStockCount
                );
                sendJson(ex, 200, json);
            }
        });

        // 3. INVENTORY / PRODUCTS
        server.createContext("/api/products", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                addCors(ex);
                String path = ex.getRequestURI().getPath();
                String method = ex.getRequestMethod().toUpperCase();

                if ("OPTIONS".equals(method)) {
                    ex.sendResponseHeaders(204, -1);
                    return;
                }

                if ("/api/products/restock".equals(path) && "POST".equals(method)) {
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    int id = parseNumberField(body, "id");
                    int addStock = parseNumberField(body, "addStock");

                    Product prod = findProduct(id);
                    if (prod != null && addStock > 0) {
                        prod.setStock(prod.getStock() + addStock);
                        saveData();
                        sendJson(ex, 200, productToJson(prod));
                    } else {
                        sendJson(ex, 400, "{\"error\":\"Invalid product or stock count\"}");
                    }
                    return;
                }

                if ("GET".equals(method)) {
                    sendJson(ex, 200, productsToJson(products));
                } else if ("POST".equals(method)) {
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String name = extractField(body, "name");
                    String sku = extractField(body, "sku");
                    String category = extractField(body, "category");
                    String priceStr = extractField(body, "price");
                    int stock = parseNumberField(body, "stock");

                    if (!name.isEmpty() && !priceStr.isEmpty()) {
                        Product p = new Product(
                                productIdSeq.incrementAndGet(),
                                name,
                                sku.isEmpty() ? "SKU-" + System.currentTimeMillis() % 10000 : sku,
                                category.isEmpty() ? "General" : category,
                                new BigDecimal(priceStr).setScale(2, RoundingMode.HALF_UP),
                                Math.max(0, stock)
                        );
                        products.add(0, p);
                        saveData();
                        sendJson(ex, 201, productToJson(p));
                    } else {
                        sendJson(ex, 400, "{\"error\":\"Missing product name or price\"}");
                    }
                } else if ("DELETE".equals(method)) {
                    int id = parseQueryParam(ex.getRequestURI().getQuery(), "id");
                    boolean removed = products.removeIf(p -> p.getId() == id);
                    if (removed) saveData();
                    sendJson(ex, 200, "{\"success\":" + removed + "}");
                } else {
                    sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
                }
            }
        });

        // 4. ORDERS
        server.createContext("/api/orders", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                addCors(ex);
                String path = ex.getRequestURI().getPath();
                String method = ex.getRequestMethod().toUpperCase();

                if ("OPTIONS".equals(method)) {
                    ex.sendResponseHeaders(204, -1);
                    return;
                }

                if ("/api/orders/status".equals(path) && "POST".equals(method)) {
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    int orderId = parseNumberField(body, "id");
                    String newStatus = extractField(body, "status");

                    Order order = findOrder(orderId);
                    if (order != null && !newStatus.isEmpty()) {
                        String oldStatus = order.getStatus();
                        order.setStatus(newStatus.toUpperCase());

                        // If cancelled, restore stock
                        if ("CANCELLED".equalsIgnoreCase(newStatus) && !"CANCELLED".equalsIgnoreCase(oldStatus)) {
                            Product p = findProduct(order.getProductId());
                            if (p != null) {
                                p.setStock(p.getStock() + order.getQuantity());
                            }
                        }
                        saveData();
                        sendJson(ex, 200, orderToJson(order));
                    } else {
                        sendJson(ex, 400, "{\"error\":\"Order not found\"}");
                    }
                    return;
                }

                if ("GET".equals(method)) {
                    sendJson(ex, 200, ordersToJson(orders));
                } else if ("POST".equals(method)) {
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    int productId = parseNumberField(body, "productId");
                    int quantity = parseNumberField(body, "quantity");
                    String customerName = extractField(body, "customerName");
                    String customerEmail = extractField(body, "customerEmail");

                    if (quantity <= 0) quantity = 1;

                    Product prod = findProduct(productId);
                    if (prod == null) {
                        sendJson(ex, 400, "{\"error\":\"Product does not exist\"}");
                        return;
                    }

                    if (prod.getStock() < quantity) {
                        sendJson(ex, 400, "{\"error\":\"Insufficient stock! Only " + prod.getStock() + " units available.\"}");
                        return;
                    }

                    // Deduct stock
                    prod.setStock(prod.getStock() - quantity);

                    BigDecimal total = prod.getPrice().multiply(new BigDecimal(quantity)).setScale(2, RoundingMode.HALF_UP);
                    String orderNumber = "ORD-" + orderIdSeq.incrementAndGet();
                    String today = LocalDate.now().toString();

                    Order newOrder = new Order(
                            orderIdSeq.get(),
                            orderNumber,
                            1,
                            customerName.isEmpty() ? "Walk-in Customer" : customerName,
                            customerEmail.isEmpty() ? "customer@example.com" : customerEmail,
                            prod.getId(),
                            prod.getName(),
                            quantity,
                            prod.getPrice(),
                            total,
                            "PAID",
                            today
                    );

                    orders.add(0, newOrder);
                    saveData();
                    sendJson(ex, 201, orderToJson(newOrder));
                } else {
                    sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
                }
            }
        });

        // 5. STATIC WEB FRONTEND
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                String path = ex.getRequestURI().getPath();
                if (path == null || path.equals("/")) {
                    path = "/index.html";
                }

                Path filePath = Paths.get("frontend", path.startsWith("/") ? path.substring(1) : path);
                File file = filePath.toFile();

                if (!file.exists() || file.isDirectory()) {
                    filePath = Paths.get("frontend", "index.html");
                    file = filePath.toFile();
                }

                if (!file.exists()) {
                    String notFound = "<h1>404 Not Found</h1>";
                    ex.sendResponseHeaders(404, notFound.length());
                    ex.getResponseBody().write(notFound.getBytes());
                    ex.getResponseBody().close();
                    return;
                }

                String contentType = "text/plain";
                if (path.endsWith(".html")) contentType = "text/html; charset=UTF-8";
                else if (path.endsWith(".css")) contentType = "text/css; charset=UTF-8";
                else if (path.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";

                byte[] bytes = Files.readAllBytes(filePath);
                ex.getResponseHeaders().set("Content-Type", contentType);
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });

        server.start();

        System.out.println("=========================================================");
        System.out.println("  KRONOS Commerce - Order & Inventory Engine             ");
        System.out.println("=========================================================");
        System.out.println("Server running on port: " + PORT);
        System.out.println("Open your browser at:   http://localhost:" + PORT);
        System.out.println("=========================================================");
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(password.hashCode());
        }
    }

    private static String userAuthJson(String token, User u) {
        return String.format(Locale.US,
                "{\"token\":\"%s\",\"user\":{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"role\":\"%s\",\"createdAt\":\"%s\"}}",
                token, u.getId(), escape(u.getName()), escape(u.getEmail()), escape(u.getRole()), escape(u.getCreatedAt())
        );
    }

    private static User getAuthenticatedUser(HttpExchange ex) {
        String authHeader = ex.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            return sessions.get(token);
        }
        return null;
    }

    private static Product findProduct(int id) {
        return products.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
    }

    private static Order findOrder(int id) {
        return orders.stream().filter(o -> o.getId() == id).findFirst().orElse(null);
    }

    private static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static int parseQueryParam(String query, String key) {
        if (query == null) return -1;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=");
            if (parts.length == 2 && parts[0].equalsIgnoreCase(key)) {
                try {
                    return Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }

    private static String productsToJson(List<Product> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(productToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String productToJson(Product p) {
        return String.format(Locale.US,
                "{\"id\":%d,\"name\":\"%s\",\"sku\":\"%s\",\"category\":\"%s\",\"price\":%.2f,\"stock\":%d}",
                p.getId(), escape(p.getName()), escape(p.getSku()), escape(p.getCategory()),
                p.getPrice().doubleValue(), p.getStock()
        );
    }

    private static String ordersToJson(List<Order> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(orderToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String orderToJson(Order o) {
        return String.format(Locale.US,
                "{\"id\":%d,\"orderNumber\":\"%s\",\"customerName\":\"%s\",\"customerEmail\":\"%s\",\"productId\":%d,\"productName\":\"%s\",\"quantity\":%d,\"unitPrice\":%.2f,\"totalAmount\":%.2f,\"status\":\"%s\",\"date\":\"%s\"}",
                o.getId(), escape(o.getOrderNumber()), escape(o.getCustomerName()), escape(o.getCustomerEmail()),
                o.getProductId(), escape(o.getProductName()), o.getQuantity(), o.getUnitPrice().doubleValue(),
                o.getTotalAmount().doubleValue(), escape(o.getStatus()), escape(o.getDate())
        );
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", " ");
    }

    private static String extractField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx == -1) return "";

        int colon = json.indexOf(":", idx + key.length());
        if (colon == -1) return "";

        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) {
            start++;
        }

        boolean inQuotes = json.charAt(start - 1) == '"';
        int end = start;

        if (inQuotes) {
            while (end < json.length() && json.charAt(end) != '"') {
                end++;
            }
        } else {
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ' ') {
                end++;
            }
        }

        return json.substring(start, end).trim();
    }

    private static int parseNumberField(String json, String field) {
        String s = extractField(json, field);
        if (s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void loadData() {
        // Starter Admin (Alex Morgan - password: admin123)
        users.add(new User(1, "Alex Morgan", "manager@example.com", hashPassword("admin123"), "Store Manager", "2026-11-01"));

        // Starter Products
        products.add(new Product(1, "Wireless Mechanical Keyboard", "KB-PRO-01", "Peripherals", new BigDecimal("129.99"), 25));
        products.add(new Product(2, "Ultra-Wide 4K Gaming Monitor", "MON-4K-02", "Displays", new BigDecimal("499.00"), 8));
        products.add(new Product(3, "Noise-Cancelling Headphones", "AUDIO-NC-03", "Audio", new BigDecimal("199.50"), 4));
        products.add(new Product(4, "Ergonomic Precision Mouse", "MSE-ERGO-04", "Peripherals", new BigDecimal("79.99"), 40));
        products.add(new Product(5, "USB-C 10-in-1 Dual Display Dock", "DOCK-10X-05", "Accessories", new BigDecimal("89.00"), 2));

        // Starter Orders
        orders.add(new Order(1001, "ORD-1001", 1, "John Doe", "john.doe@gmail.com", 1, "Wireless Mechanical Keyboard", 1, new BigDecimal("129.99"), new BigDecimal("129.99"), "PAID", "2026-11-05"));
        orders.add(new Order(1002, "ORD-1002", 1, "Sarah Connor", "sarah.c@gmail.com", 2, "Ultra-Wide 4K Gaming Monitor", 1, new BigDecimal("499.00"), new BigDecimal("499.00"), "SHIPPED", "2026-11-06"));
        orders.add(new Order(1003, "ORD-1003", 1, "Michael Scott", "michael@dunder.com", 4, "Ergonomic Precision Mouse", 2, new BigDecimal("79.99"), new BigDecimal("159.98"), "PAID", "2026-11-07"));

        saveData();
    }

    private static void saveData() {
        try {
            File f = new File(DATA_FILE);
            if (f.getParentFile() != null && !f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }
            String json = String.format("{\"products\":%s,\"orders\":%s}", productsToJson(products), ordersToJson(orders));
            Files.writeString(f.toPath(), json, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }
}
