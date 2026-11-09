# KRONOS Commerce — Order & Inventory System

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/asritha726/kronos-commerce)

A production-grade, minimalist Order & Inventory management system built with:
- **Backend:** Java (Port `8080`)
- **Authentication:** Secure Email & Password (Log In & Sign Up with SHA-256 password hashing)
- **Frontend:** Modern HTML5, CSS3, JavaScript (Monochrome White & Black design)
- **Database:** MySQL 8.0 schema (`database/schema.sql`) + auto-persisted local storage

---

## 🎨 Design System
- **Color Palette:** Pure White (`#FFFFFF`), Solid Black (`#000000`), Subtle Gray (`#E5E7EB`).
- **Brand:** **KRONOS** (Commerce & Inventory).
- **Interface:** High-contrast, clean typography, distraction-free.

---

## 🔐 Authentication & Profile
1. **Log In:** Uses registered email and password with SHA-256 encryption.
2. **Sign Up:** Direct account creation for new store managers.
3. **User Profile:** Interactive profile modal displaying Avatar, Name, Email, Role (`Store Manager`), User ID, Member Date, and Sign Out.

---

## 📁 Project Structure

```
Project/
├── backend/
│   ├── src/com/fintech/oms/
│   │   ├── Main.java         # Java REST API Server (port 8080) & Auth controller
│   │   ├── Product.java      # Product model (name, sku, category, price, stock)
│   │   ├── Order.java        # Order model (customer, product, qty, total, status)
│   │   └── User.java         # User profile model (auth & session)
│   └── bin/                  # Compiled Java bytecode
├── database/
│   ├── schema.sql            # MySQL 8.0 schema
│   ├── setup_mysql.bat       # 1-Click MySQL importer
│   └── setup_mysql.ps1       # PowerShell MySQL importer
├── frontend/
│   ├── index.html            # KRONOS Commerce interface & Auth Dialog
│   ├── style.css             # White & Black monochrome design
│   └── app.js                # Auth, Profile, Inventory & Order logic
├── run.bat                   # 1-Click Windows runner
├── run.ps1                   # 1-Click PowerShell runner
├── sources.txt               # Source manifest
└── README.md
```

---

## 🚀 How to Run

1. Double-click **`run.bat`** (or run `.\run.bat` in CMD / PowerShell).
2. Open your browser at:
   **[http://localhost:8080](http://localhost:8080)**
3. Log in with:
   - **Email:** `manager@example.com` / **Password:** `admin123`
   - Or click the **Sign Up** tab to create a brand-new store manager account!

---

## ☁️ 1-Click Cloud Deployment (Render)

1. Click the **Deploy to Render** button above, or go to **[render.com](https://render.com)**.
2. Connect your GitHub repository `asritha726/kronos-commerce`.
3. Render automatically reads `render.yaml` and builds the Docker container.
4. Your application will be live at `https://kronos-commerce.onrender.com`!
