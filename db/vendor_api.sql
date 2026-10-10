-- =====================================================================
-- vendor_api.sql
--
-- Database features of the vendor API: indexes, an audit log table,
-- views, triggers, a stored procedure, a scheduled event and users.
-- Uses only the SQL taught in the course slides (Tietokantaratkaisut),
-- plus SIGNAL in triggers (agreed separately).
--
-- Run ONCE on a fresh database that contains the course data:
--
--   DROP DATABASE IF EXISTS asd;
--   CREATE DATABASE asd;
--   USE asd;
--   (import vendor_db.sql)
--
--   mariadb asd -u root -p < db/vendor_api.sql
--
-- The users at the end are created for the database `asd`; change the
-- name and the passwords if needed.
-- =====================================================================


-- =====================================================================
-- 1. INDEXES  (slides: Indeksointi, Kyselyn optimointi)
--
-- Primary keys and foreign keys are indexed automatically (InnoDB).
-- These indexes are for fields that the API's repeated queries use in
-- WHERE (search condition) and ORDER BY (sorting).
-- Check with EXPLAIN, e.g.:
--   EXPLAIN SELECT * FROM orders WHERE status = 'NEW' ORDER BY order_date DESC;
-- =====================================================================

-- GET /api/orders?status=...  newest first
-- (WHERE status = ? ORDER BY order_date)
CREATE INDEX idx_orders_status_date
    ON orders(status, order_date);

-- GET /api/orders?customerId=...  newest first
-- (WHERE customer_id = ? ORDER BY order_date)
CREATE INDEX idx_orders_customer_date
    ON orders(customer_id, order_date);

-- GET /api/products?supplierId=...  sorted by name
-- (WHERE supplier_id = ? ORDER BY name)
CREATE INDEX idx_products_supplier_name
    ON products(supplier_id, name);

-- GET /api/products  sorted by name  (ORDER BY name)
CREATE INDEX idx_products_name
    ON products(name);

-- GET /api/customers?email=...  (WHERE email = ?) among 100 000 customers
CREATE INDEX idx_customers_email
    ON customers(email);


-- =====================================================================
-- 2. AUDIT LOG TABLE
--
-- Every change of an order's status is written here by the trigger
-- trigger_order_status_change (section 4). The log is the history of
-- the orders' statuses: when each status was valid.
-- ON DELETE CASCADE: deleting an order also deletes its log rows.
-- =====================================================================

CREATE TABLE order_status_log (
    id INT NOT NULL AUTO_INCREMENT,
    order_id INT NOT NULL,
    old_status VARCHAR(50),
    new_status VARCHAR(50),
    changed_at DATETIME,
    PRIMARY KEY (id),
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- one order's status history: WHERE order_id = ? ORDER BY changed_at
CREATE INDEX idx_statuslog_order_changed
    ON order_status_log(order_id, changed_at);

-- the purge event (section 6): WHERE changed_at < ?
CREATE INDEX idx_statuslog_changed
    ON order_status_log(changed_at);


-- =====================================================================
-- 3. VIEWS  (slides: Näkymät)
--
-- MERGE     = the query on the view is converted into a query on the
--             physical tables ("kyselyn muuntaminen").
-- TEMPTABLE = the view is computed into a temporary table
--             ("näkymän esineellistäminen"); used for the views with
--             aggregate data (GROUP BY), which cannot be merged anyway.
-- The read-only user vendor_report (section 7) can read only these.
-- =====================================================================

-- Products with their category and supplier names.
CREATE ALGORITHM = MERGE VIEW product_catalog
AS
SELECT
    p.id,
    p.name AS product_name,
    p.description,
    p.price,
    p.stock_quantity,
    pc.name AS category_name,
    s.name AS supplier_name
FROM products p
LEFT JOIN productcategories pc
    ON p.category_id = pc.id
LEFT JOIN suppliers s
    ON p.supplier_id = s.id;

-- One row per order: customer, number of order lines, units and total.
-- (An order without lines gets NULL as units and total.)
CREATE ALGORITHM = TEMPTABLE VIEW order_summary
AS
SELECT
    o.id AS order_id,
    o.customer_id,
    c.first_name,
    c.last_name,
    o.order_date,
    o.delivery_date,
    o.status,
    COUNT(oi.product_id) AS item_lines,
    SUM(oi.quantity) AS total_units,
    SUM(oi.quantity * oi.unit_price) AS total_amount
FROM orders o
INNER JOIN customers c
    ON c.id = o.customer_id
LEFT JOIN orderitems oi
    ON oi.order_id = o.id
GROUP BY o.id, o.customer_id, c.first_name, c.last_name,
         o.order_date, o.delivery_date, o.status;

-- Sales per product, cancelled orders excluded.
-- (A product appears once it has been sold at least once.)
CREATE ALGORITHM = TEMPTABLE VIEW product_sales
AS
SELECT
    p.id AS product_id,
    p.name AS product_name,
    p.supplier_id,
    p.category_id,
    COUNT(*) AS order_count,
    SUM(oi.quantity) AS units_sold,
    SUM(oi.quantity * oi.unit_price) AS revenue
FROM orderitems oi
INNER JOIN orders o
    ON o.id = oi.order_id
INNER JOIN products p
    ON p.id = oi.product_id
WHERE o.status <> 'CANCELLED'
GROUP BY p.id, p.name, p.supplier_id, p.category_id;


-- =====================================================================
-- 4. TRIGGERS  (slides: Aktiiviset tietokannat)
--
-- Rules that keep the data consistent no matter which client changes it:
--   * every order status change is logged
--   * an order's status can only change NEW -> SHIPPED or NEW -> CANCELLED
--   * an order line reserves stock; a line larger than the stock is rejected
--   * cancelling an order, or deleting a line of a NEW order, returns the stock
-- SIGNAL SQLSTATE '45000' rejects the statement with an error message;
-- the API returns it to the client as HTTP 409.
-- =====================================================================

delimiter //

-- Logs every status change (old -> new) with the time of the change.
CREATE TRIGGER trigger_order_status_change
AFTER UPDATE ON orders
FOR EACH ROW
BEGIN
    IF old.status <> new.status THEN
        INSERT INTO order_status_log
            (order_id, old_status, new_status, changed_at)
        VALUES
            (new.id, old.status, new.status, NOW());
    END IF;
END//

-- Only NEW -> SHIPPED and NEW -> CANCELLED are allowed.
CREATE TRIGGER trigger_order_status_transition
BEFORE UPDATE ON orders
FOR EACH ROW
BEGIN
    IF old.status <> new.status
       AND NOT (old.status = 'NEW' AND new.status IN ('SHIPPED', 'CANCELLED')) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Order status cannot change: only NEW -> SHIPPED and NEW -> CANCELLED are allowed';
    END IF;
END//

-- An order line must fit in the stock (lines of a cancelled order don't reserve stock).
CREATE TRIGGER trigger_orderitem_check_stock
BEFORE INSERT ON orderitems
FOR EACH ROW
BEGIN
    IF (SELECT stock_quantity FROM products WHERE id = new.product_id) < new.quantity
       AND (SELECT status FROM orders WHERE id = new.order_id) <> 'CANCELLED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Insufficient stock for the ordered product';
    END IF;
END//

delimiter ;

-- A new order line reserves stock.
CREATE TRIGGER trigger_orderitem_reserve_stock
AFTER INSERT ON orderitems
FOR EACH ROW
UPDATE products
SET stock_quantity = stock_quantity - new.quantity
WHERE id = new.product_id
  AND (SELECT status FROM orders WHERE id = new.order_id) <> 'CANCELLED';

-- Deleting a line of a NEW order returns its stock.
-- (Shipped goods have left; cancelled orders were already restocked.)
CREATE TRIGGER trigger_orderitem_release_stock
AFTER DELETE ON orderitems
FOR EACH ROW
UPDATE products
SET stock_quantity = stock_quantity + old.quantity
WHERE id = old.product_id
  AND (SELECT status FROM orders WHERE id = old.order_id) = 'NEW';

-- Cancelling an order returns the stock of all its lines.
CREATE TRIGGER trigger_order_cancel_restock
AFTER UPDATE ON orders
FOR EACH ROW
UPDATE products
SET stock_quantity = stock_quantity +
    (SELECT oi.quantity
     FROM orderitems oi
     WHERE oi.order_id = new.id AND oi.product_id = products.id)
WHERE id IN (SELECT product_id FROM orderitems WHERE order_id = new.id)
  AND new.status = 'CANCELLED'
  AND old.status <> 'CANCELLED';


-- =====================================================================
-- 5. STORED PROCEDURE WITH A TRANSACTION  (slides: Aktiiviset tietokannat,
--    Transaktiot, Samanaikaisuuden hallinta)
--
-- cancel_order(id) cancels a NEW order as one transaction:
--   SELECT ... FOR UPDATE puts a write lock on the order row, so another
--   transaction cannot ship or cancel the same order at the same time.
--   The UPDATE only changes a NEW order; the triggers restock and log it.
-- The SELECT returns the order's status before the cancellation.
--
--   CALL cancel_order(200001);
-- =====================================================================

delimiter //

CREATE PROCEDURE cancel_order (IN p_order_id INT)
BEGIN
    START TRANSACTION;

    SELECT id, status
    FROM orders
    WHERE id = p_order_id
    FOR UPDATE;

    UPDATE orders
    SET status = 'CANCELLED'
    WHERE id = p_order_id AND status = 'NEW';

    COMMIT;
END//

delimiter ;


-- =====================================================================
-- 6. SCHEDULED EVENT  (slides: Aktiiviset tietokannat)
--
-- Once a day, delete status log rows older than two years (730 days).
-- Needs the event scheduler:  SET GLOBAL event_scheduler = ON;
-- =====================================================================

CREATE EVENT event_purge_order_status_log
ON SCHEDULE EVERY 1 DAY
DO
DELETE FROM order_status_log
WHERE changed_at < SUBTIME(NOW(), "730 0:0:0");


-- =====================================================================
-- 7. USERS AND PRIVILEGES  (slides: Tietoturva ja tietosuoja, Johdanto)
--
-- Smallest necessary privileges:
--   vendor_api    the API: data access (CRUD) only, no structure changes,
--                 plus the right to run the procedure
--   vendor_report read-only reporting: the views, and from customers only
--                 the names (no e-mail addresses or phone numbers)
-- =====================================================================

CREATE USER 'vendor_api'@'localhost' IDENTIFIED BY 'change-me-api';
GRANT SELECT, INSERT, UPDATE, DELETE ON asd.* TO 'vendor_api'@'localhost';
GRANT EXECUTE ON PROCEDURE asd.cancel_order TO 'vendor_api'@'localhost';

CREATE USER 'vendor_report'@'localhost' IDENTIFIED BY 'change-me-report';
GRANT SELECT ON asd.product_catalog TO 'vendor_report'@'localhost';
GRANT SELECT ON asd.order_summary TO 'vendor_report'@'localhost';
GRANT SELECT ON asd.product_sales TO 'vendor_report'@'localhost';
GRANT SELECT (id, first_name, last_name) ON asd.customers TO 'vendor_report'@'localhost';


-- =====================================================================
-- EXAMPLE QUERIES (for trying the features out; not run by this script)
--
-- Indexes:
--   EXPLAIN SELECT * FROM orders WHERE status = 'NEW' ORDER BY order_date DESC;
--   EXPLAIN SELECT * FROM products WHERE supplier_id = 1 ORDER BY name;
--   EXPLAIN SELECT * FROM customers WHERE email = 'vsmith1@example.com';
--
-- Views:
--   SELECT * FROM product_catalog WHERE supplier_name = 'Polar Electronics Oy';
--   SELECT * FROM order_summary WHERE customer_id = 1 ORDER BY order_date DESC;
--   SELECT * FROM product_sales ORDER BY units_sold DESC;
--
-- Triggers (inside a transaction that is rolled back):
--   START TRANSACTION;
--   SELECT stock_quantity FROM products WHERE id = 1;
--   INSERT INTO orders (customer_id, order_date, shipping_address_id, status)
--       VALUES (1, NOW(), 1, 'NEW');
--   SELECT MAX(id) FROM orders;                         -- the new order's id
--   INSERT INTO orderitems VALUES (<new id>, 1, 2, 22.22);
--   SELECT stock_quantity FROM products WHERE id = 1;   -- 2 less
--   UPDATE orders SET status = 'CANCELLED' WHERE id = <new id>;
--   SELECT stock_quantity FROM products WHERE id = 1;   -- back again
--   SELECT * FROM order_status_log WHERE order_id = <new id>;
--   UPDATE orders SET status = 'NEW' WHERE id = <new id>;  -- error (SIGNAL)
--   ROLLBACK;
--
-- Procedure:
--   CALL cancel_order(<id of a NEW order>);
-- =====================================================================
