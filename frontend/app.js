/**
 * KRONOS Commerce - Application Controller
 * Handles Normal Auth (Login/Signup), User Profile, Inventory, and Orders
 */

let authToken = localStorage.getItem('kronos_auth_token') || null;
let currentUser = null;

let allProducts = [];
let allOrders = [];
let activeTab = 'inventory';

document.addEventListener('DOMContentLoaded', async () => {
    initAuthListeners();
    initInvoiceModal();
    initTabListeners();
    initProfileModalListeners();
    initFormListeners();

    if (authToken) {
        await verifySession();
    } else {
        showAuthScreen();
    }
});

// ==========================================
// 1. AUTHENTICATION (LOGIN & SIGN UP)
// ==========================================
function initAuthListeners() {
    // Switch between Login and Sign Up tabs
    const tabLogin = document.getElementById('tabBtnLogin');
    const tabSignup = document.getElementById('tabBtnSignup');
    const formLogin = document.getElementById('formNormalLogin');
    const formSignup = document.getElementById('formNormalSignup');
    const errorBanner = document.getElementById('authErrorBanner');

    tabLogin?.addEventListener('click', () => {
        tabLogin.classList.add('active');
        tabSignup.classList.remove('active');
        formLogin.classList.remove('hidden');
        formSignup.classList.add('hidden');
        errorBanner.classList.add('hidden');
    });

    tabSignup?.addEventListener('click', () => {
        tabSignup.classList.add('active');
        tabLogin.classList.remove('active');
        formSignup.classList.remove('hidden');
        formLogin.classList.add('hidden');
        errorBanner.classList.add('hidden');
    });

    // Normal Log In Submission
    formLogin?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('loginEmail').value.trim();
        const password = document.getElementById('loginPassword').value;

        await submitAuth('/api/auth/login', { email, password });
    });

    // Normal Sign Up Submission
    formSignup?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const name = document.getElementById('signupName').value.trim();
        const email = document.getElementById('signupEmail').value.trim();
        const password = document.getElementById('signupPassword').value;

        await submitAuth('/api/auth/signup', { name, email, password });
    });

    // Sign Out Buttons
    document.getElementById('btnLogout')?.addEventListener('click', handleSignOut);
    document.getElementById('btnModalSignOut')?.addEventListener('click', handleSignOut);
}

async function submitAuth(url, payload) {
    const errorBanner = document.getElementById('authErrorBanner');
    errorBanner.classList.add('hidden');

    try {
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const data = await res.json();

        if (res.ok) {
            authToken = data.token;
            currentUser = data.user;
            localStorage.setItem('kronos_auth_token', authToken);
            showMainWorkspace();
            await loadDashboard();
        } else {
            errorBanner.textContent = data.error || 'Authentication failed.';
            errorBanner.classList.remove('hidden');
        }
    } catch (err) {
        console.error('Auth request error:', err);
        errorBanner.textContent = 'Server connection error. Please try again.';
        errorBanner.classList.remove('hidden');
    }
}

async function verifySession() {
    try {
        const res = await fetch('/api/auth/me', {
            headers: { 'Authorization': `Bearer ${authToken}` }
        });

        if (res.ok) {
            const data = await res.json();
            if (data.authenticated) {
                currentUser = data.user;
                showMainWorkspace();
                await loadDashboard();
                return;
            }
        }
    } catch (ignored) {}

    handleSignOut();
}

function handleSignOut() {
    if (authToken) {
        fetch('/api/auth/logout', {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${authToken}` }
        }).catch(() => {});
    }
    authToken = null;
    currentUser = null;
    localStorage.removeItem('kronos_auth_token');
    closeProfileModal();
    showAuthScreen();
}

function showAuthScreen() {
    document.getElementById('authScreen').classList.remove('hidden');
    document.getElementById('mainAppScreen').classList.add('hidden');
}

function showMainWorkspace() {
    document.getElementById('authScreen').classList.add('hidden');
    document.getElementById('mainAppScreen').classList.remove('hidden');

    if (currentUser) {
        document.getElementById('headerUserName').textContent = currentUser.name || 'Store Manager';
        document.getElementById('headerUserRole').textContent = currentUser.role || 'Store Manager';
        document.getElementById('headerAvatar').textContent = (currentUser.name ? currentUser.name[0] : 'M').toUpperCase();
    }
}

// ==========================================
// 2. USER PROFILE MODAL
// ==========================================
function initProfileModalListeners() {
    document.getElementById('btnOpenProfile')?.addEventListener('click', openProfileModal);
    document.getElementById('btnCloseProfile')?.addEventListener('click', closeProfileModal);

    // Close on background click
    document.getElementById('profileModal')?.addEventListener('click', (e) => {
        if (e.target.id === 'profileModal') closeProfileModal();
    });
}

function openProfileModal() {
    if (!currentUser) return;

    document.getElementById('modalProfileAvatar').textContent = (currentUser.name ? currentUser.name[0] : 'M').toUpperCase();
    document.getElementById('modalProfileName').textContent = currentUser.name || 'Store Manager';
    document.getElementById('modalProfileEmail').textContent = currentUser.email || '';
    document.getElementById('modalProfileRole').textContent = currentUser.role || 'Store Manager';
    document.getElementById('modalProfileId').textContent = '#' + (currentUser.id || '1');
    document.getElementById('modalProfileCreated').textContent = currentUser.createdAt || '2026-11-01';

    document.getElementById('profileModal').classList.remove('hidden');
}

function closeProfileModal() {
    document.getElementById('profileModal')?.classList.add('hidden');
}

// ==========================================
// 3. NAVIGATION TABS
// ==========================================
function initTabListeners() {
    document.querySelectorAll('.nav-button').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.nav-button').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            activeTab = btn.getAttribute('data-tab');

            if (activeTab === 'inventory') {
                document.getElementById('tabInventory').classList.remove('hidden');
                document.getElementById('tabOrders').classList.add('hidden');
            } else {
                document.getElementById('tabInventory').classList.add('hidden');
                document.getElementById('tabOrders').classList.remove('hidden');
            }
        });
    });

    // Real-time Search
    document.getElementById('inventorySearch')?.addEventListener('input', (e) => {
        renderProductsTable(e.target.value.toLowerCase().trim());
    });

    document.getElementById('ordersSearch')?.addEventListener('input', (e) => {
        renderOrdersTable(e.target.value.toLowerCase().trim());
    });

    // CSV Exports
    document.getElementById('btnExportInventoryCsv')?.addEventListener('click', () => {
        if (!allProducts.length) return alert('No products to export.');
        let csv = 'ID,Product Name,SKU,Category,Price,Stock\n';
        allProducts.forEach(p => {
            csv += `${p.id},"${p.name.replace(/"/g, '""')}","${p.sku}","${p.category}",${p.price},${p.stock}\n`;
        });
        downloadCsvFile(csv, 'kronos_inventory.csv');
    });

    document.getElementById('btnExportOrdersCsv')?.addEventListener('click', () => {
        if (!allOrders.length) return alert('No orders to export.');
        let csv = 'Order Number,Date,Customer,Email,Product,Quantity,Total Amount,Status\n';
        allOrders.forEach(o => {
            csv += `"${o.orderNumber}",${o.date},"${o.customerName.replace(/"/g, '""')}","${o.customerEmail}","${o.productName.replace(/"/g, '""')}",${o.quantity},${o.totalAmount},${o.status}\n`;
        });
        downloadCsvFile(csv, 'kronos_orders.csv');
    });
}

function downloadCsvFile(content, filename) {
    const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
}

// ==========================================
// 4. DATA SYNCHRONIZATION
// ==========================================
async function loadDashboard() {
    try {
        const [summaryRes, prodRes, orderRes] = await Promise.all([
            fetch('/api/summary', { headers: getAuthHeaders() }),
            fetch('/api/products', { headers: getAuthHeaders() }),
            fetch('/api/orders', { headers: getAuthHeaders() })
        ]);

        const summary = await summaryRes.json();
        allProducts = await prodRes.json();
        allOrders = await orderRes.json();

        // Update KPIs
        document.getElementById('metricTotalRevenue').textContent = formatMoney(summary.totalRevenue);
        document.getElementById('metricTotalOrders').textContent = summary.totalOrders || 0;
        document.getElementById('metricTotalProducts').textContent = summary.totalProducts || 0;
        document.getElementById('metricLowStockCount').textContent = summary.lowStockCount || 0;

        renderProductsTable();
        renderOrdersTable();
        populateProductDropdown();
    } catch (err) {
        console.error('Error loading dashboard data:', err);
    }
}

function getAuthHeaders() {
    const headers = { 'Content-Type': 'application/json' };
    if (authToken) headers['Authorization'] = `Bearer ${authToken}`;
    return headers;
}

// ==========================================
// 5. INVENTORY CATALOG
// ==========================================
function renderProductsTable(query = '') {
    const tbody = document.getElementById('productsTableBody');
    if (!tbody) return;

    let filtered = allProducts;
    if (query) {
        filtered = allProducts.filter(p =>
            p.name.toLowerCase().includes(query) ||
            p.sku.toLowerCase().includes(query) ||
            p.category.toLowerCase().includes(query)
        );
    }

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center" style="padding: 24px; color: #888;">No products found</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered.map(p => {
        let badge = '<span class="badge badge-in-stock">IN STOCK</span>';
        if (p.stock === 0) badge = '<span class="badge badge-out-stock">OUT OF STOCK</span>';
        else if (p.stock <= 5) badge = '<span class="badge badge-low-stock">LOW STOCK</span>';

        return `
            <tr>
                <td><strong>${escapeHtml(p.name)}</strong></td>
                <td><code class="font-mono">${escapeHtml(p.sku)}</code></td>
                <td>${escapeHtml(p.category)}</td>
                <td class="text-right font-mono">${formatMoney(p.price)}</td>
                <td class="text-right font-mono"><strong>${p.stock}</strong></td>
                <td>${badge}</td>
                <td class="text-center">
                    <button class="btn-action" onclick="restockItem(${p.id})">+10</button>
                    <button class="btn-action" onclick="deleteItem(${p.id})">Delete</button>
                </td>
            </tr>
        `;
    }).join('');
}

window.restockItem = async function(id) {
    try {
        const res = await fetch('/api/products/restock', {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({ id, addStock: 10 })
        });
        if (res.ok) await loadDashboard();
    } catch (err) {
        console.error('Restock error:', err);
    }
};

window.deleteItem = async function(id) {
    if (!confirm('Are you sure you want to delete this product?')) return;
    try {
        const res = await fetch(`/api/products?id=${id}`, {
            method: 'DELETE',
            headers: getAuthHeaders()
        });
        if (res.ok) await loadDashboard();
    } catch (err) {
        console.error('Delete product error:', err);
    }
};

// ==========================================
// 6. ORDERS LEDGER
// ==========================================
function populateProductDropdown() {
    const sel = document.getElementById('orderProductSelect');
    if (!sel) return;

    sel.innerHTML = allProducts.map(p => 
        `<option value="${p.id}">${escapeHtml(p.name)} - ${formatMoney(p.price)} (${p.stock} in stock)</option>`
    ).join('');
}

function renderOrdersTable(query = '') {
    const tbody = document.getElementById('ordersTableBody');
    if (!tbody) return;

    let filtered = allOrders;
    if (query) {
        filtered = allOrders.filter(o =>
            o.orderNumber.toLowerCase().includes(query) ||
            o.customerName.toLowerCase().includes(query) ||
            o.productName.toLowerCase().includes(query) ||
            o.status.toLowerCase().includes(query)
        );
    }

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" class="text-center" style="padding: 24px; color: #888;">No orders recorded</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered.map(o => {
        let badge = `<span class="badge badge-paid">${o.status}</span>`;
        if (o.status === 'SHIPPED') badge = `<span class="badge badge-shipped">SHIPPED</span>`;
        else if (o.status === 'CANCELLED') badge = `<span class="badge badge-cancelled">CANCELLED</span>`;

        let action = '';
        if (o.status === 'PAID') {
            action = `
                <button class="btn-action" onclick="changeOrderStatus(${o.id}, 'SHIPPED')">Ship</button>
                <button class="btn-action" onclick="changeOrderStatus(${o.id}, 'CANCELLED')">Cancel</button>
                <button class="btn-action" onclick="viewInvoice('${escapeHtml(o.orderNumber)}')">Invoice</button>
            `;
        } else if (o.status === 'SHIPPED') {
            action = `
                <button class="btn-action" onclick="viewInvoice('${escapeHtml(o.orderNumber)}')">Invoice</button>
                <span style="font-size:0.75rem; color:#888; margin-left:4px;">Fulfilled</span>
            `;
        } else {
            action = `
                <button class="btn-action" onclick="viewInvoice('${escapeHtml(o.orderNumber)}')">Invoice</button>
                <span style="font-size:0.75rem; color:#888; margin-left:4px;">Voided</span>
            `;
        }

        return `
            <tr>
                <td><strong class="font-mono">${escapeHtml(o.orderNumber)}</strong></td>
                <td>${escapeHtml(o.date)}</td>
                <td>
                    <div><strong>${escapeHtml(o.customerName)}</strong></div>
                    <div style="font-size:0.72rem; color:#6B7280;">${escapeHtml(o.customerEmail)}</div>
                </td>
                <td>${escapeHtml(o.productName)}</td>
                <td class="text-center font-mono"><strong>${o.quantity}</strong></td>
                <td class="text-right font-mono"><strong>${formatMoney(o.totalAmount)}</strong></td>
                <td>${badge}</td>
                <td class="text-center">${action}</td>
            </tr>
        `;
    }).join('');
}

window.changeOrderStatus = async function(id, status) {
    try {
        const res = await fetch('/api/orders/status', {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({ id, status })
        });
        if (res.ok) await loadDashboard();
    } catch (err) {
        console.error('Order status update error:', err);
    }
};

// ==========================================
// 7. FORM SUBMISSIONS
// ==========================================
function initFormListeners() {
    // Add Product
    document.getElementById('addProductForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();

        const name = document.getElementById('prodName').value.trim();
        const sku = document.getElementById('prodSku').value.trim();
        const category = document.getElementById('prodCategory').value;
        const price = parseFloat(document.getElementById('prodPrice').value);
        const stock = parseInt(document.getElementById('prodStock').value);

        if (!name || isNaN(price) || price <= 0 || isNaN(stock) || stock < 0) {
            alert('Please enter valid product details.');
            return;
        }

        try {
            const res = await fetch('/api/products', {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify({ name, sku, category, price, stock })
            });

            if (res.ok) {
                e.target.reset();
                await loadDashboard();
            } else {
                alert('Failed to add product');
            }
        } catch (err) {
            console.error('Add product error:', err);
        }
    });

    // Create Order
    document.getElementById('createOrderForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();

        const customerName = document.getElementById('orderCustomerName').value.trim();
        const customerEmail = document.getElementById('orderCustomerEmail').value.trim();
        const productId = parseInt(document.getElementById('orderProductSelect').value);
        const quantity = parseInt(document.getElementById('orderQuantity').value);

        if (!customerName || isNaN(productId) || isNaN(quantity) || quantity <= 0) {
            alert('Please select a valid product and quantity.');
            return;
        }

        try {
            const res = await fetch('/api/orders', {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify({ customerName, customerEmail, productId, quantity })
            });

            if (res.ok) {
                e.target.reset();
                document.getElementById('orderQuantity').value = '1';
                await loadDashboard();
            } else {
                const err = await res.json();
                alert(err.error || 'Failed to place order');
            }
        } catch (err) {
            console.error('Order creation error:', err);
        }
    });
}

// ==========================================
// 8. ORDER INVOICE MODAL
// ==========================================
function initInvoiceModal() {
    const modal = document.getElementById('invoiceModal');
    const btnClose = document.getElementById('btnCloseInvoice');
    const btnDismiss = document.getElementById('btnDismissInvoice');

    const closeModal = () => modal?.classList.add('hidden');

    btnClose?.addEventListener('click', closeModal);
    btnDismiss?.addEventListener('click', closeModal);

    modal?.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });
}

window.viewInvoice = function(orderNumber) {
    const order = allOrders.find(o => o.orderNumber === orderNumber);
    if (!order) return;

    const modal = document.getElementById('invoiceModal');
    const content = document.getElementById('invoiceContent');
    if (!modal || !content) return;

    let badgeClass = 'badge-paid';
    if (order.status === 'SHIPPED') badgeClass = 'badge-shipped';
    else if (order.status === 'CANCELLED') badgeClass = 'badge-cancelled';

    content.innerHTML = `
        <div class="invoice-meta-grid">
            <div class="invoice-meta-col">
                <h4>BILLED TO</h4>
                <p><strong>${escapeHtml(order.customerName)}</strong></p>
                <p class="font-mono text-muted" style="font-size:0.75rem;">${escapeHtml(order.customerEmail)}</p>
            </div>
            <div class="invoice-meta-col" style="text-align: right;">
                <h4>INVOICE DETAILS</h4>
                <p>Order #: <strong class="font-mono">${escapeHtml(order.orderNumber)}</strong></p>
                <p>Date: <span class="font-mono">${escapeHtml(order.date)}</span></p>
                <p style="margin-top:4px;">Status: <span class="badge ${badgeClass}">${escapeHtml(order.status)}</span></p>
            </div>
        </div>

        <table class="invoice-items-table">
            <thead>
                <tr>
                    <th>ITEM DESCRIPTION</th>
                    <th class="text-center">QTY</th>
                    <th class="text-right">UNIT PRICE</th>
                    <th class="text-right">AMOUNT</th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td><strong>${escapeHtml(order.productName)}</strong></td>
                    <td class="text-center font-mono">${order.quantity}</td>
                    <td class="text-right font-mono">${formatMoney(order.unitPrice)}</td>
                    <td class="text-right font-mono"><strong>${formatMoney(order.totalAmount)}</strong></td>
                </tr>
            </tbody>
        </table>

        <div class="invoice-summary-box">
            <div class="invoice-summary-row">
                <span class="text-muted">Subtotal:</span>
                <span class="font-mono">${formatMoney(order.totalAmount)}</span>
            </div>
            <div class="invoice-summary-row">
                <span class="text-muted">Taxes & Fees:</span>
                <span class="font-mono">$0.00</span>
            </div>
            <div class="invoice-summary-row total-row">
                <span>Total:</span>
                <span class="font-mono">${formatMoney(order.totalAmount)}</span>
            </div>
        </div>
    `;

    modal.classList.remove('hidden');
};

// Helpers
function formatMoney(amount) {
    const val = parseFloat(amount) || 0;
    return '$' + val.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>"']/g, m => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    }[m]));
}
