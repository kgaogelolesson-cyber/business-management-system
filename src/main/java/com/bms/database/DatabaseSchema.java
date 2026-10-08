package com.bms.database;

/**
 * The SQL that builds the database (the design from Phase 3).
 * Each entry is ONE statement, because JDBC runs one statement at a time.
 * "IF NOT EXISTS" makes it safe to run on every start-up.
 */
public final class DatabaseSchema {

    private DatabaseSchema() {
    }

    public static final String[] STATEMENTS = {

        // ---------- EMPLOYEES ----------
        """
        CREATE TABLE IF NOT EXISTS employees (
            employee_id  INTEGER PRIMARY KEY AUTOINCREMENT,
            first_name   TEXT    NOT NULL CHECK (length(trim(first_name)) > 0),
            last_name    TEXT    NOT NULL CHECK (length(trim(last_name)) > 0),
            email        TEXT,
            phone        TEXT,
            position     TEXT,
            department   TEXT,
            hire_date    TEXT,
            is_active    INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1))
        )
        """,

        // ---------- USERS ----------
        """
        CREATE TABLE IF NOT EXISTS users (
            user_id        INTEGER PRIMARY KEY AUTOINCREMENT,
            username       TEXT    NOT NULL UNIQUE COLLATE NOCASE
                                   CHECK (length(trim(username)) >= 3),
            password_hash  TEXT    NOT NULL,
            salt           TEXT    NOT NULL,
            role           TEXT    NOT NULL CHECK (role IN ('ADMIN', 'EMPLOYEE')),
            employee_id    INTEGER UNIQUE,
            is_active      INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
            created_at     TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
            FOREIGN KEY (employee_id) REFERENCES employees (employee_id) ON DELETE RESTRICT
        )
        """,

        // ---------- CUSTOMERS ----------
        """
        CREATE TABLE IF NOT EXISTS customers (
            customer_id  INTEGER PRIMARY KEY AUTOINCREMENT,
            first_name   TEXT    NOT NULL CHECK (length(trim(first_name)) > 0),
            last_name    TEXT    NOT NULL CHECK (length(trim(last_name)) > 0),
            email        TEXT,
            phone        TEXT,
            address      TEXT,
            is_active    INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
            created_at   TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
        )
        """,

        // ---------- SUPPLIERS ----------
        """
        CREATE TABLE IF NOT EXISTS suppliers (
            supplier_id     INTEGER PRIMARY KEY AUTOINCREMENT,
            supplier_name   TEXT    NOT NULL CHECK (length(trim(supplier_name)) > 0),
            contact_person  TEXT,
            email           TEXT,
            phone           TEXT,
            address         TEXT,
            is_active       INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1))
        )
        """,

        // ---------- PRODUCTS ----------
        """
        CREATE TABLE IF NOT EXISTS products (
            product_id         INTEGER PRIMARY KEY AUTOINCREMENT,
            product_name       TEXT    NOT NULL CHECK (length(trim(product_name)) > 0),
            category           TEXT,
            description        TEXT,
            price_cents        INTEGER NOT NULL CHECK (price_cents >= 0),
            quantity_in_stock  INTEGER NOT NULL DEFAULT 0 CHECK (quantity_in_stock >= 0),
            reorder_level      INTEGER NOT NULL DEFAULT 5 CHECK (reorder_level >= 0),
            supplier_id        INTEGER,
            is_active          INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
            FOREIGN KEY (supplier_id) REFERENCES suppliers (supplier_id) ON DELETE RESTRICT
        )
        """,

        // ---------- ORDERS ----------
        """
        CREATE TABLE IF NOT EXISTS orders (
            order_id            INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_id         INTEGER NOT NULL,
            employee_id         INTEGER NOT NULL,
            order_date          TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
            status              TEXT    NOT NULL DEFAULT 'COMPLETED'
                                        CHECK (status IN ('COMPLETED', 'CANCELLED')),
            total_amount_cents  INTEGER NOT NULL DEFAULT 0 CHECK (total_amount_cents >= 0),
            cancelled_at        TEXT,
            cancelled_by        INTEGER,
            CHECK (
                (status = 'COMPLETED' AND cancelled_at IS NULL     AND cancelled_by IS NULL) OR
                (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL)
            ),
            FOREIGN KEY (customer_id)  REFERENCES customers (customer_id) ON DELETE RESTRICT,
            FOREIGN KEY (employee_id)  REFERENCES employees (employee_id) ON DELETE RESTRICT,
            FOREIGN KEY (cancelled_by) REFERENCES users (user_id)         ON DELETE RESTRICT
        )
        """,

        // ---------- ORDER_ITEMS ----------
        """
        CREATE TABLE IF NOT EXISTS order_items (
            order_item_id     INTEGER PRIMARY KEY AUTOINCREMENT,
            order_id          INTEGER NOT NULL,
            product_id        INTEGER NOT NULL,
            quantity          INTEGER NOT NULL CHECK (quantity > 0),
            unit_price_cents  INTEGER NOT NULL CHECK (unit_price_cents >= 0),
            subtotal_cents    INTEGER NOT NULL,
            UNIQUE (order_id, product_id),
            CHECK (subtotal_cents = quantity * unit_price_cents),
            FOREIGN KEY (order_id)   REFERENCES orders (order_id)     ON DELETE CASCADE,
            FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE RESTRICT
        )
        """,

        // ---------- STOCK_ADJUSTMENTS ----------
        """
        CREATE TABLE IF NOT EXISTS stock_adjustments (
            adjustment_id    INTEGER PRIMARY KEY AUTOINCREMENT,
            product_id       INTEGER NOT NULL,
            user_id          INTEGER NOT NULL,
            quantity_change  INTEGER NOT NULL CHECK (quantity_change <> 0),
            reason           TEXT    NOT NULL CHECK (length(trim(reason)) > 0),
            adjusted_at      TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
            FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE RESTRICT,
            FOREIGN KEY (user_id)    REFERENCES users (user_id)       ON DELETE RESTRICT
        )
        """,

        // ---------- SETTINGS ----------
        """
        CREATE TABLE IF NOT EXISTS settings (
            setting_key    TEXT PRIMARY KEY,
            setting_value  TEXT NOT NULL
        )
        """,

        // ---------- INDEXES (faster searching and joining) ----------
        "CREATE INDEX IF NOT EXISTS idx_employees_last_name  ON employees (last_name)",
        "CREATE INDEX IF NOT EXISTS idx_employees_department ON employees (department)",
        "CREATE INDEX IF NOT EXISTS idx_customers_last_name  ON customers (last_name)",
        "CREATE INDEX IF NOT EXISTS idx_customers_email      ON customers (email)",
        "CREATE INDEX IF NOT EXISTS idx_customers_phone      ON customers (phone)",
        "CREATE INDEX IF NOT EXISTS idx_suppliers_name       ON suppliers (supplier_name)",
        "CREATE INDEX IF NOT EXISTS idx_products_name        ON products (product_name)",
        "CREATE INDEX IF NOT EXISTS idx_products_category    ON products (category)",
        "CREATE INDEX IF NOT EXISTS idx_products_supplier    ON products (supplier_id)",
        "CREATE INDEX IF NOT EXISTS idx_orders_customer      ON orders (customer_id)",
        "CREATE INDEX IF NOT EXISTS idx_orders_employee      ON orders (employee_id)",
        "CREATE INDEX IF NOT EXISTS idx_orders_date          ON orders (order_date)",
        "CREATE INDEX IF NOT EXISTS idx_orders_status        ON orders (status)",
        "CREATE INDEX IF NOT EXISTS idx_order_items_product  ON order_items (product_id)",
        "CREATE INDEX IF NOT EXISTS idx_stock_adj_product    ON stock_adjustments (product_id)",

        // ---------- VIEWS (saved queries used by inventory and reports) ----------
        """
        CREATE VIEW IF NOT EXISTS v_inventory AS
        SELECT  p.product_id,
                p.product_name,
                p.category,
                p.quantity_in_stock,
                p.reorder_level,
                CASE
                    WHEN p.quantity_in_stock = 0                 THEN 'OUT OF STOCK'
                    WHEN p.quantity_in_stock <= p.reorder_level  THEN 'LOW STOCK'
                    ELSE 'IN STOCK'
                END AS stock_status,
                s.supplier_name
        FROM    products p
        LEFT JOIN suppliers s ON s.supplier_id = p.supplier_id
        WHERE   p.is_active = 1
        """,

        """
        CREATE VIEW IF NOT EXISTS v_order_summary AS
        SELECT  o.order_id,
                o.order_date,
                o.status,
                o.total_amount_cents,
                c.customer_id,
                c.first_name || ' ' || c.last_name AS customer_name,
                e.employee_id,
                e.first_name || ' ' || e.last_name AS employee_name
        FROM    orders o
        JOIN    customers c ON c.customer_id = o.customer_id
        JOIN    employees e ON e.employee_id = o.employee_id
        """,

        """
        CREATE VIEW IF NOT EXISTS v_order_item_details AS
        SELECT  oi.order_item_id,
                oi.order_id,
                oi.product_id,
                p.product_name,
                oi.quantity,
                oi.unit_price_cents,
                oi.subtotal_cents
        FROM    order_items oi
        JOIN    products p ON p.product_id = oi.product_id
        """,

        // ---------- DEFAULT SETTINGS (INSERT OR IGNORE = do nothing if they already exist) ----------
        """
        INSERT OR IGNORE INTO settings (setting_key, setting_value) VALUES
            ('business_name', 'My Business'),
            ('currency_symbol', 'R'),
            ('default_low_stock_level', '5')
        """
    };
}
