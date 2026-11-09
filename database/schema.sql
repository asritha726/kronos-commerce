-- ====================================================
-- KRONOS Commerce & Inventory System (MySQL 8.0)
-- ====================================================

CREATE DATABASE IF NOT EXISTS inventory_oms_db;
USE inventory_oms_db;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) DEFAULT 'Store Manager',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Products / Inventory Table
CREATE TABLE IF NOT EXISTS products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    sku VARCHAR(50) NOT NULL UNIQUE,
    category VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Orders Table
CREATE TABLE IF NOT EXISTS orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    user_id INT NOT NULL,
    customer_name VARCHAR(150) NOT NULL,
    customer_email VARCHAR(150) NOT NULL,
    product_id INT NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status ENUM('PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED') DEFAULT 'PAID',
    order_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- Starter Catalog
INSERT INTO products (name, sku, category, price, stock) VALUES
('Wireless Mechanical Keyboard', 'KB-PRO-01', 'Peripherals', 129.99, 25),
('Ultra-Wide 4K Gaming Monitor', 'MON-4K-02', 'Displays', 499.00, 8),
('Noise-Cancelling Headphones', 'AUDIO-NC-03', 'Audio', 199.50, 4),
('Ergonomic Precision Mouse', 'MSE-ERGO-04', 'Peripherals', 79.99, 40),
('USB-C 10-in-1 Dual Display Dock', 'DOCK-10X-05', 'Accessories', 89.00, 2);

-- Starter Demo Manager (Password: admin123)
-- SHA-256 for admin123 is 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9
INSERT INTO users (id, name, email, password_hash, role) VALUES
(1, 'Alex Morgan', 'manager@example.com', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Store Manager');

-- Starter Orders
INSERT INTO orders (order_number, user_id, customer_name, customer_email, product_id, product_name, quantity, unit_price, total_amount, status, order_date) VALUES
('ORD-1001', 1, 'John Doe', 'john.doe@gmail.com', 1, 'Wireless Mechanical Keyboard', 1, 129.99, 129.99, 'PAID', '2026-11-05'),
('ORD-1002', 1, 'Sarah Connor', 'sarah.c@gmail.com', 2, 'Ultra-Wide 4K Gaming Monitor', 1, 499.00, 499.00, 'SHIPPED', '2026-11-06'),
('ORD-1003', 1, 'Michael Scott', 'michael@dunder.com', 4, 'Ergonomic Precision Mouse', 2, 79.99, 159.98, 'PAID', '2026-11-07');
