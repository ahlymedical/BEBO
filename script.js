// ==========================================
// STATE MANAGEMENT & DATA PERSISTENCE
// ==========================================
let items = JSON.parse(localStorage.getItem('wims_items')) || [];
let transactions = JSON.parse(localStorage.getItem('wims_transactions')) || [];
let scannerMode = 'in'; // 'in' or 'out'
let html5QrcodeScanner = null;

function saveData() {
    localStorage.setItem('wims_items', JSON.stringify(items));
    localStorage.setItem('wims_transactions', JSON.stringify(transactions));
    updateDashboard();
    renderInventory();
    renderReports();
}

// ==========================================
// UI & NAVIGATION LOGIC
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
    // Theme initialization
    if (localStorage.getItem('theme') === 'dark' || (!('theme' in localStorage) && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
        document.documentElement.classList.add('dark');
        updateThemeUI(true);
    } else {
        document.documentElement.classList.remove('dark');
        updateThemeUI(false);
    }

    // Event Listeners
    document.getElementById('themeToggleBtn').addEventListener('click', toggleTheme);
    document.getElementById('openSidebarBtn').addEventListener('click', toggleSidebar);
    document.getElementById('closeSidebarBtn').addEventListener('click', toggleSidebar);
    document.getElementById('sidebarOverlay').addEventListener('click', toggleSidebar);
    document.getElementById('addItemForm').addEventListener('submit', handleAddItem);
    document.getElementById('manualScanForm').addEventListener('submit', handleManualScan);

    // Initial renders
    updateDashboard();
    renderInventory();
    renderReports();
});

// Navigation logic (SPA Router)
function navigate(viewId) {
    // Stop scanner if navigating away
    if (viewId !== 'scanner' && html5QrcodeScanner) {
        html5QrcodeScanner.clear().catch(error => console.error("Failed to clear scanner", error));
        html5QrcodeScanner = null;
    }

    // Hide all views
    document.querySelectorAll('.view-section').forEach(section => {
        section.classList.remove('active');
        section.classList.add('hidden');
    });

    // Remove active class from all nav buttons
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.classList.remove('active');
    });

    // Show target view
    const targetView = document.getElementById(`view-${viewId}`);
    targetView.classList.remove('hidden');
    // small delay for animation
    setTimeout(() => targetView.classList.add('active'), 10);

    // Highlight active nav button
    document.querySelector(`.nav-btn[data-target="${viewId}"]`).classList.add('active');

    // Close sidebar on mobile after navigation
    if (window.innerWidth < 768) {
        toggleSidebar();
    }

    // Initialize scanner if navigating to scanner view
    if (viewId === 'scanner' && !html5QrcodeScanner) {
        initScanner();
    }
}

// Mobile sidebar toggle
function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebarOverlay');

    if (sidebar.classList.contains('-translate-x-full')) {
        sidebar.classList.remove('-translate-x-full');
        overlay.classList.remove('hidden');
    } else {
        sidebar.classList.add('-translate-x-full');
        overlay.classList.add('hidden');
    }
}

// Theme toggling
function toggleTheme() {
    const isDark = document.documentElement.classList.toggle('dark');
    localStorage.setItem('theme', isDark ? 'dark' : 'light');
    updateThemeUI(isDark);
}

function updateThemeUI(isDark) {
    const icon = document.getElementById('themeIcon');
    const text = document.getElementById('themeText');
    if (isDark) {
        icon.className = 'fa-solid fa-sun text-yellow-400';
        text.textContent = 'Light Mode';
    } else {
        icon.className = 'fa-solid fa-moon text-gray-700';
        text.textContent = 'Dark Mode';
    }
}

// Toast Notifications
function showToast(message, type = 'success') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');

    let bgClass, iconClass;
    if (type === 'success') {
        bgClass = 'bg-emerald-500';
        iconClass = 'fa-circle-check';
    } else if (type === 'error') {
        bgClass = 'bg-red-500';
        iconClass = 'fa-circle-xmark';
    } else {
        bgClass = 'bg-amber-500';
        iconClass = 'fa-triangle-exclamation';
    }

    toast.className = `${bgClass} text-white px-4 py-3 rounded-lg shadow-lg flex items-center gap-3 toast`;
    toast.innerHTML = `<i class="fa-solid ${iconClass}"></i> <span>${message}</span>`;

    container.appendChild(toast);

    // Trigger animation
    setTimeout(() => toast.classList.add('show'), 10);

    // Remove toast
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// ==========================================
// INVENTORY MANAGEMENT
// ==========================================
function toggleAddItemModal() {
    const modal = document.getElementById('addItemModal');
    const form = document.getElementById('addItemForm');

    if (modal.classList.contains('hidden')) {
        modal.classList.remove('hidden');
        form.reset();
        // small delay for animation
        setTimeout(() => modal.classList.add('modal-open'), 10);
    } else {
        modal.classList.remove('modal-open');
        setTimeout(() => modal.classList.add('hidden'), 300);
    }
}

function handleAddItem(e) {
    e.preventDefault();

    const name = document.getElementById('itemName').value.trim();
    const barcode = document.getElementById('itemBarcode').value.trim();
    const stock = parseInt(document.getElementById('itemStock').value, 10);
    const minAlert = parseInt(document.getElementById('itemMinAlert').value, 10);

    // Check if barcode already exists
    if (items.some(item => item.barcode === barcode)) {
        showToast('Barcode ID already exists!', 'error');
        return;
    }

    const newItem = {
        id: Date.now().toString(),
        name,
        barcode,
        stock,
        minAlert
    };

    items.push(newItem);

    // Log initial stock addition if stock > 0
    if (stock > 0) {
        logTransaction(newItem.id, newItem.name, 'in', stock);
    }

    saveData();
    toggleAddItemModal();
    showToast('Item registered successfully', 'success');
}

function deleteItem(barcode) {
    if(confirm('Are you sure you want to delete this item?')) {
        items = items.filter(item => item.barcode !== barcode);
        saveData();
        showToast('Item deleted', 'success');
    }
}

function renderInventory() {
    const tbody = document.getElementById('inventoryTableBody');
    tbody.innerHTML = '';

    if (items.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center py-8 text-gray-500">No items in inventory. Register a new item to get started.</td></tr>`;
        return;
    }

    items.forEach(item => {
        const isLowStock = item.stock <= item.minAlert;
        const statusBadge = isLowStock
            ? `<span class="bg-red-100 text-red-800 text-xs font-medium px-2.5 py-0.5 rounded dark:bg-red-900/30 dark:text-red-400 border border-red-200 dark:border-red-800">Low Stock</span>`
            : `<span class="bg-green-100 text-green-800 text-xs font-medium px-2.5 py-0.5 rounded dark:bg-green-900/30 dark:text-green-400 border border-green-200 dark:border-green-800">Healthy</span>`;

        const tr = document.createElement('tr');
        tr.className = `border-b border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800/50 ${isLowStock ? 'bg-red-50 dark:bg-red-900/10' : ''}`;

        tr.innerHTML = `
            <td class="py-3 px-4 font-medium">${item.name}</td>
            <td class="py-3 px-4 font-mono text-sm">${item.barcode}</td>
            <td class="py-3 px-4 font-bold ${isLowStock ? 'text-red-500' : ''}">${item.stock}</td>
            <td class="py-3 px-4">${item.minAlert}</td>
            <td class="py-3 px-4 text-center">${statusBadge}</td>
            <td class="py-3 px-4 text-center">
                <button onclick="deleteItem('${item.barcode}')" class="text-red-500 hover:text-red-700 transition-colors" title="Delete Item">
                    <i class="fa-solid fa-trash"></i>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

// ==========================================
// SCANNER LOGIC
// ==========================================
function setScannerMode(mode) {
    scannerMode = mode;
    const btnIn = document.getElementById('modeInBtn');
    const btnOut = document.getElementById('modeOutBtn');
    const label = document.getElementById('currentModeLabel');

    if (mode === 'in') {
        btnIn.className = "flex-1 py-3 rounded-lg font-semibold border-2 border-green-500 bg-green-500 text-white transition-all flex items-center justify-center gap-2";
        btnOut.className = "flex-1 py-3 rounded-lg font-semibold border-2 border-gray-300 dark:border-gray-600 text-gray-500 dark:text-gray-400 hover:border-red-500 hover:text-red-500 transition-all flex items-center justify-center gap-2";
        label.className = "text-green-500 uppercase";
        label.textContent = "Receive (IN) - Stock will increase";
    } else {
        btnOut.className = "flex-1 py-3 rounded-lg font-semibold border-2 border-red-500 bg-red-500 text-white transition-all flex items-center justify-center gap-2";
        btnIn.className = "flex-1 py-3 rounded-lg font-semibold border-2 border-gray-300 dark:border-gray-600 text-gray-500 dark:text-gray-400 hover:border-green-500 hover:text-green-500 transition-all flex items-center justify-center gap-2";
        label.className = "text-red-500 uppercase";
        label.textContent = "Issue (OUT) - Stock will decrease";
    }
}

function processScan(decodedText) {
    const itemIndex = items.findIndex(i => i.barcode === decodedText);

    if (itemIndex === -1) {
        showToast(`Barcode ${decodedText} not found in inventory!`, 'error');
        // Play error sound (optional enhancement)
        return;
    }

    const item = items[itemIndex];

    if (scannerMode === 'in') {
        items[itemIndex].stock += 1;
        logTransaction(item.id, item.name, 'in', 1);
        showToast(`Received: ${item.name} (+1). New Stock: ${items[itemIndex].stock}`, 'success');
    } else {
        if (items[itemIndex].stock > 0) {
            items[itemIndex].stock -= 1;
            logTransaction(item.id, item.name, 'out', 1);
            showToast(`Issued: ${item.name} (-1). New Stock: ${items[itemIndex].stock}`, 'success');

            // Trigger alert if it hits threshold
            if (items[itemIndex].stock <= item.minAlert) {
                setTimeout(() => showToast(`Warning: ${item.name} is low on stock!`, 'warning'), 1000);
            }
        } else {
            showToast(`Cannot issue ${item.name}. Stock is already 0!`, 'error');
            return;
        }
    }

    saveData();
}

function initScanner() {
    html5QrcodeScanner = new Html5QrcodeScanner(
        "reader",
        { fps: 10, qrbox: { width: 250, height: 250 }, rememberLastUsedCamera: true },
        /* verbose= */ false
    );

    // Debounce scans to prevent multiple triggers for one read
    let lastScanData = null;
    let lastScanTime = 0;

    html5QrcodeScanner.render((decodedText, decodedResult) => {
        const now = Date.now();
        // Prevent scanning same code within 2 seconds
        if (decodedText !== lastScanData || (now - lastScanTime) > 2000) {
            lastScanData = decodedText;
            lastScanTime = now;
            processScan(decodedText);
        }
    }, (error) => {
        // ignore scan failures
    });
}

function handleManualScan(e) {
    e.preventDefault();
    const input = document.getElementById('manualBarcode');
    const barcode = input.value.trim();
    if(barcode) {
        processScan(barcode);
        input.value = '';
        input.focus();
    }
}

// ==========================================
// REPORTS & DASHBOARD
// ==========================================
function logTransaction(itemId, itemName, type, quantity) {
    const transaction = {
        id: Date.now().toString(),
        timestamp: new Date().toISOString(),
        itemId,
        itemName,
        type, // 'in' or 'out'
        quantity
    };
    transactions.unshift(transaction); // Add to beginning
    // Keep only last 100 transactions to prevent localstorage overflow
    if (transactions.length > 100) transactions.pop();
}

function clearHistory() {
    if(confirm('Are you sure you want to clear all transaction history?')) {
        transactions = [];
        saveData();
        showToast('Transaction history cleared', 'success');
    }
}

function updateDashboard() {
    // Calculate metrics
    const totalUnique = items.length;
    const totalVolume = items.reduce((sum, item) => sum + item.stock, 0);
    const lowStockItems = items.filter(item => item.stock <= item.minAlert);

    // Update metric cards
    document.getElementById('dashTotalUnique').textContent = totalUnique;
    document.getElementById('dashTotalVolume').textContent = totalVolume;
    document.getElementById('dashLowStock').textContent = lowStockItems.length;

    // Update low stock table
    const tbody = document.getElementById('dashboardLowStockTable');
    tbody.innerHTML = '';

    if (lowStockItems.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" class="text-center py-4 text-gray-500">All items are sufficiently stocked.</td></tr>`;
        return;
    }

    lowStockItems.forEach(item => {
        const tr = document.createElement('tr');
        tr.className = "border-b border-gray-200 dark:border-gray-700 bg-red-50 dark:bg-red-900/10";
        tr.innerHTML = `
            <td class="py-3 px-4 font-medium">${item.name}</td>
            <td class="py-3 px-4 font-mono text-sm">${item.barcode}</td>
            <td class="py-3 px-4 font-bold text-red-500">${item.stock}</td>
            <td class="py-3 px-4">${item.minAlert}</td>
        `;
        tbody.appendChild(tr);
    });
}

function renderReports() {
    const tbody = document.getElementById('reportsTableBody');
    tbody.innerHTML = '';

    if (transactions.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-8 text-gray-500">No transaction history available.</td></tr>`;
        return;
    }

    transactions.forEach(tx => {
        const date = new Date(tx.timestamp);
        const dateString = date.toLocaleString();

        const actionBadge = tx.type === 'in'
            ? `<span class="bg-green-100 text-green-800 text-xs font-medium px-2.5 py-0.5 rounded dark:bg-green-900/30 dark:text-green-400"><i class="fa-solid fa-arrow-turn-down"></i> IN</span>`
            : `<span class="bg-red-100 text-red-800 text-xs font-medium px-2.5 py-0.5 rounded dark:bg-red-900/30 dark:text-red-400"><i class="fa-solid fa-arrow-turn-up"></i> OUT</span>`;

        const qtyPrefix = tx.type === 'in' ? '+' : '-';
        const qtyColor = tx.type === 'in' ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400';

        const tr = document.createElement('tr');
        tr.className = "border-b border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800/50";
        tr.innerHTML = `
            <td class="py-3 px-4 text-sm text-gray-500 dark:text-gray-400">${dateString}</td>
            <td class="py-3 px-4 font-mono text-sm">${tx.itemId}</td>
            <td class="py-3 px-4 font-medium">${tx.itemName}</td>
            <td class="py-3 px-4 text-center">${actionBadge}</td>
            <td class="py-3 px-4 text-center font-bold ${qtyColor}">${qtyPrefix}${tx.quantity}</td>
        `;
        tbody.appendChild(tr);
    });
}