CREATE INDEX idx_orders_status_date
    ON orders(status, order_date);

CREATE INDEX idx_orders_customer_date
    ON orders(customer_id, order_date);

CREATE INDEX idx_products_supplier_name
    ON products(supplier_id, name);

CREATE INDEX idx_products_name
    ON products(name);

CREATE INDEX idx_customers_email
    ON customers(email);

CREATE TABLE order_status_log (
    id INT NOT NULL AUTO_INCREMENT,
    order_id INT NOT NULL,
    old_status VARCHAR(50),
    new_status VARCHAR(50),
    changed_at DATETIME,
    PRIMARY KEY (id),
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_statuslog_order_changed
    ON order_status_log(order_id, changed_at);

CREATE INDEX idx_statuslog_changed
    ON order_status_log(changed_at);

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

delimiter //

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

CREATE TRIGGER trigger_orderitem_reserve_stock
AFTER INSERT ON orderitems
FOR EACH ROW
UPDATE products
SET stock_quantity = stock_quantity - new.quantity
WHERE id = new.product_id
  AND (SELECT status FROM orders WHERE id = new.order_id) <> 'CANCELLED';

CREATE TRIGGER trigger_orderitem_release_stock
AFTER DELETE ON orderitems
FOR EACH ROW
UPDATE products
SET stock_quantity = stock_quantity + old.quantity
WHERE id = old.product_id
  AND (SELECT status FROM orders WHERE id = old.order_id) = 'NEW';

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

CREATE EVENT event_purge_order_status_log
ON SCHEDULE EVERY 1 DAY
DO
DELETE FROM order_status_log
WHERE changed_at < SUBTIME(NOW(), "730 0:0:0");

CREATE USER 'vendor_api'@'localhost' IDENTIFIED BY 'change-me-api';
GRANT SELECT, INSERT, UPDATE, DELETE ON * TO 'vendor_api'@'localhost';
GRANT EXECUTE ON PROCEDURE cancel_order TO 'vendor_api'@'localhost';

CREATE USER 'vendor_report'@'localhost' IDENTIFIED BY 'change-me-report';
GRANT SELECT ON product_catalog TO 'vendor_report'@'localhost';
GRANT SELECT ON order_summary TO 'vendor_report'@'localhost';
GRANT SELECT ON product_sales TO 'vendor_report'@'localhost';
GRANT SELECT (id, first_name, last_name) ON customers TO 'vendor_report'@'localhost';