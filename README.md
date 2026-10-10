# Vendor API: web store backend

A CRUD REST API for a small web store, built on the course's relational schema (`vendor_db.sql`,
MariaDB). It is written from the **vendors' (suppliers') point of view**: a vendor manages its
profile and address, its products and their categories, and the orders that contain its products.
Customers and their addresses have CRUD endpoints too, because orders need them.

The API itself is plain CRUD (create, read, update, delete). The business rules live **in the
database**: triggers reserve and restore stock, only allow valid order status changes and log every
status change with its time, and foreign keys reject invalid references. The
[Database features](#database-features) section documents everything that is not visible from the
API: indexes, views, triggers, transactions, the status history, the stored procedure, the scheduled
event and security.

**Stack:** Java 21, Spring Boot 3.5 (Web, Data JPA / Hibernate, Validation), MariaDB 11.x, bash + curl + jq (tests).

---

## Contents

1. [Feature overview](#feature-overview)
2. [Getting started](#getting-started)
3. [Project structure](#project-structure)
4. [API reference](#api-reference)
   - [Conventions](#conventions)
   - [Endpoint overview](#endpoint-overview)
   - [Suppliers](#suppliers)
   - [Products](#products)
   - [Product categories](#product-categories)
   - [Orders](#orders)
   - [Customers](#customers)
5. [Database features](#database-features)
   - [Schema](#schema)
   - [Indexes](#indexes)
   - [Triggers](#triggers)
   - [Transactions and locking](#transactions-and-locking)
   - [Views](#views)
   - [Order status history](#order-status-history)
   - [Scheduled event](#scheduled-event)
   - [Security](#security)
   - [Query techniques in the application](#query-techniques-in-the-application)
   - [Avoiding the N+1 query problem](#avoiding-the-n1-query-problem)
6. [Testing](#testing)
7. [Known limitations](#known-limitations)

---

## Feature overview

| Topic | Where it is used |
|---|---|
| **CRUD** | 33 endpoints: suppliers (+ address), products, product categories, orders (+ order items), customers (+ addresses) |
| **Combining tables** | Products embed their category. Orders embed customer, shipping address and order items. `GET /api/orders?supplierId=` finds orders through order items -> products -> supplier. |
| **Triggers** | 6 triggers fire behind the order CRUD: stock check, reservation and release, restock on cancel, allowed status transitions, status log ([details](#triggers)) |
| **Indexes** | 7 indexes, each added for a specific API query, with `EXPLAIN` checks ([details](#indexes)) |
| **Transactions** | `POST /api/orders` is one transaction with `SELECT … FOR UPDATE` row locks. The `cancel_order` stored procedure uses `START TRANSACTION … SELECT … FOR UPDATE … COMMIT` ([details](#transactions-and-locking)) |
| **Time dimension** | `order_status_log` keeps every order status change with its time, written by a trigger ([details](#order-status-history)) |
| **Views** | `product_catalog` (`MERGE`), `order_summary` and `product_sales` (`TEMPTABLE`): reporting views for the read-only `vendor_report` account ([details](#views)) |
| **Stored procedure / event** | `cancel_order` procedure; a daily event that purges old audit rows |
| **Security** | Smallest necessary privileges for the DB users, views and field-level rights as an access layer, parameterized queries, input validation, no SQL details in error responses ([details](#security)) |

---

## Getting started

### 1. Database

```sh
mariadb -u root -p -e "DROP DATABASE IF EXISTS asd; CREATE DATABASE asd;"
mariadb asd -u root -p < vendor_db.sql

mariadb asd -u root -p < db/vendor_api.sql

mariadb -u root -p -e "SET GLOBAL event_scheduler = ON"
```

`db/vendor_api.sql` is run once, on a fresh database. The end of the script has example queries
(`EXPLAIN`, the views, the triggers inside a rolled-back transaction, `CALL cancel_order`).

### 2. API

```sh
cp src/main/resources/application.properties.example src/main/resources/application.properties

mvn spring-boot:run          # http://localhost:8080
```

`application.properties` is gitignored so credentials stay out of version control.
`spring.jpa.hibernate.ddl-auto=none`: the schema is owned by the SQL scripts, and Hibernate must not
alter it.

### 3. Tests and dashboard

```sh
./test.sh
```

---

## Project structure

```
vendor-api/
├── README.md                       this file
├── vendor_db.sql                   schema + sample data
├── db/
│   └── vendor_api.sql              indexes, log table, views, triggers, procedure, event, users
├── src/main/dev/onesnzeroes/vendor/endpoint/
│   ├── supplier/                   suppliers CRUD + their address
│   ├── product/                    products CRUD
│   ├── productcategory/            categories CRUD
│   ├── order/                      orders CRUD (with order items)
│   ├── orderitem/                  order line entity + DTOs
│   ├── customer/                   customers CRUD + their addresses
│   ├── address/                    address entities, repositories and request DTO
│   └── common/                     paging, error handling, delete response
├── test.sh                         end-to-end API tests
└── dashboard/                      React + Vite vendor dashboard
```

Each resource has the same layers: **controller** (HTTP) -> **service** (rules, transactions) ->
**repository** (Spring Data JPA). Request bodies are DTOs with Bean Validation annotations, never
entities.

---

## API reference

### Conventions

- **Base URL:** `http://localhost:8080/api`
- **JSON** request and response bodies with **camelCase** field names.
- **Date-times** are ISO-8601 local date-times: `2026-09-11T14:30:00`.
- **Paging** (orders and customers: `?page=0&size=20`, size max 100) returns:

  ```json
  { "content": [ ... ], "page": 0, "size": 20, "totalElements": 200000, "totalPages": 10000 }
  ```

- **Status codes:** `200 OK`, `201 Created` (POST that creates a row), `400` invalid input or a
  referenced row that doesn't exist, `404` the row in the URL doesn't exist, `409` the operation
  breaks a database rule (foreign key, trigger, insufficient stock),
  `500` unexpected database error.
- **Errors** use the RFC 7807 problem-detail format:

  ```json
  {
    "type": "about:blank",
    "title": "Conflict",
    "status": 409,
    "detail": "Insufficient stock for product 12: 3 available, 5 requested",
    "instance": "/api/orders"
  }
  ```

  Validation errors list the fields: `"detail": "email: must be a well-formed email address"`.
- **Delete** endpoints return `{ "message": "<Thing> deleted successfully", "id": <id> }`.
- Numbers in the response examples below are illustrative.

### Endpoint overview

| Method | Path | Purpose | Database behind it |
|---|---|---|---|
| GET | `/api/suppliers` | List suppliers | |
| GET | `/api/suppliers/{id}` | Get a supplier | |
| POST | `/api/suppliers` | Create a supplier | |
| PUT | `/api/suppliers/{id}` | Update a supplier | |
| DELETE | `/api/suppliers/{id}` | Delete a supplier (409 if it has products or an address) | FK |
| GET | `/api/suppliers/{id}/address` | Get the supplier's address | |
| PUT | `/api/suppliers/{id}/address` | Create or replace the supplier's address | |
| DELETE | `/api/suppliers/{id}/address` | Delete the supplier's address | |
| GET | `/api/products` | List products, optional `supplierId` / `categoryId` | `idx_products_supplier_name`, `idx_products_name` |
| GET | `/api/products/{id}` | Get a product | |
| POST | `/api/products` | Create a product | |
| PUT | `/api/products/{id}` | Update a product | |
| DELETE | `/api/products/{id}` | Delete a product (409 if ordered) | FK |
| GET | `/api/productcategories` | List categories | |
| GET | `/api/productcategories/{id}` | Get a category | |
| POST | `/api/productcategories` | Create a category | |
| PUT | `/api/productcategories/{id}` | Update a category | |
| DELETE | `/api/productcategories/{id}` | Delete a category (409 if it has products) | FK |
| GET | `/api/orders` | List orders, paged, optional `status` / `customerId` / `supplierId` | `idx_orders_status_date`, `idx_orders_customer_date` |
| GET | `/api/orders/{id}` | Get an order with its items | |
| POST | `/api/orders` | Create an order with its items | **transaction**, row locks, stock **triggers** |
| PUT | `/api/orders/{id}` | Update an order (ship, cancel, dates, address) | status, restock and log **triggers** |
| DELETE | `/api/orders/{id}` | Delete an order and its items | stock release **trigger** |
| GET | `/api/customers` | List customers, paged, optional `email` | `idx_customers_email` |
| GET | `/api/customers/{id}` | Get a customer | |
| POST | `/api/customers` | Create a customer | |
| PUT | `/api/customers/{id}` | Update a customer | |
| DELETE | `/api/customers/{id}` | Delete a customer (409 with orders or addresses) | FK |
| GET | `/api/customers/{id}/addresses` | List a customer's addresses | |
| GET | `/api/customers/{id}/addresses/{addressId}` | Get one address | |
| POST | `/api/customers/{id}/addresses` | Add an address | |
| PUT | `/api/customers/{id}/addresses/{addressId}` | Update an address | |
| DELETE | `/api/customers/{id}/addresses/{addressId}` | Delete an address (409 if orders ship to it) | FK |

---

### Suppliers

A supplier is a vendor selling on the platform.

```json
{ "id": 1, "name": "Polar Electronics Oy", "contactName": "Mia Manninen", "phone": "0401001000", "email": "mia.manninen@polarelec.fi" }
```

| Endpoint | Request | Response |
|---|---|---|
| `GET /api/suppliers` | | list of suppliers |
| `GET /api/suppliers/{id}` | | supplier, `404` if missing |
| `POST /api/suppliers` | body below | `201` + supplier |
| `PUT /api/suppliers/{id}` | body below | updated supplier |
| `DELETE /api/suppliers/{id}` | | `{ "message": "Supplier deleted successfully", "id": 1 }`, `409` while it has products or an address |

| Field | Type | Required | Rules |
|---|---|---|---|
| `name` | string | yes | not blank, max 255 |
| `contactName` | string | no | max 100 |
| `phone` | string | no | max 30 |
| `email` | string | no | valid e-mail, max 255 |

```http
POST /api/suppliers
{ "name": "Polar Electronics Oy", "contactName": "Mia Manninen", "phone": "0401001000", "email": "mia@polarelec.fi" }
```

#### Supplier address

A supplier has at most one address (`supplieraddresses`, one-to-one).

```json
{ "id": 1, "streetAddress": "Teollisuuskatu 5", "postalCode": "00510", "city": "Helsinki", "country": "Finland" }
```

| Endpoint | Request | Response |
|---|---|---|
| `GET /api/suppliers/{id}/address` | | address, `404` if the supplier has none |
| `PUT /api/suppliers/{id}/address` | `streetAddress`, `city` (required), `postalCode`, `country` | the address, created or replaced (the id stays the same on replace) |
| `DELETE /api/suppliers/{id}/address` | | `{ "message": "Supplier address deleted successfully", "id": 1 }` |

---

### Products

```json
{
  "id": 12,
  "name": "Wireless Keyboard",
  "description": "Compact wireless keyboard",
  "price": 39.90,
  "stockQuantity": 25,
  "category": { "id": 1, "name": "Elektroniikka", "description": "..." },
  "supplierId": 1
}
```

`category` is the full category object (or `null`).

#### `GET /api/products`

All products sorted by name. Optional filters:

| Query param | Description |
|---|---|
| `supplierId` | only this vendor's products |
| `categoryId` | only products in this category |

```http
GET /api/products?supplierId=1
```

#### `GET /api/products/{id}`

One product, `404` if missing.

#### `POST /api/products` · `PUT /api/products/{id}`

Create (`201`) or update a product.

| Field | Type | Required | Rules |
|---|---|---|---|
| `name` | string | yes | not blank |
| `description` | string | no | |
| `price` | number | yes | ≥ 0 |
| `stockQuantity` | integer | yes | ≥ 0 |
| `categoryId` | integer | no | must exist (else 400) |
| `supplierId` | integer | no | must exist (else 400) |

```json
{
  "name": "Wireless Keyboard",
  "description": "Compact wireless keyboard",
  "price": 39.90,
  "stockQuantity": 25,
  "categoryId": 1,
  "supplierId": 1
}
```

Response: the product.

#### `DELETE /api/products/{id}`

`{ "message": "Product deleted successfully", "id": 12 }`. `409` if the product appears in any order.

---

### Product categories

```json
{ "id": 5, "name": "Electronics", "description": "Electronic devices and accessories" }
```

| Endpoint | Request | Response |
|---|---|---|
| `GET /api/productcategories` | | list of categories |
| `GET /api/productcategories/{id}` | | category, `404` if missing |
| `POST /api/productcategories` | `{ "name": "Electronics", "description": "..." }` (`name` required, max 100) | `201` + category |
| `PUT /api/productcategories/{id}` | same as POST | updated category |
| `DELETE /api/productcategories/{id}` | | `{ "message": "Product category deleted successfully", "id": 5 }`, `409` if it still has products |

---

### Orders

An order combines four tables: `orders`, `customers`, `customeraddresses` and `orderitems`.

```json
{
  "id": 200001,
  "customer": { "id": 1, "firstName": "Dustin", "lastName": "Carey", "email": "vsmith1@example.com", "phone": "001-789-824-7188x591" },
  "orderDate": "2026-09-11T10:00:00",
  "deliveryDate": null,
  "shippingAddress": { "id": 1, "streetAddress": "39650 Harrington Plains Suite 474", "postalCode": "95942", "city": "Larryview", "country": "Guadeloupe" },
  "status": "NEW",
  "orderItems": [
    { "id": { "orderId": 200001, "productId": 12 }, "productId": 12, "quantity": 3, "unitPrice": 59.90 }
  ]
}
```

`orderItems` is included by `GET /api/orders/{id}` and `POST /api/orders`, and is `null` in lists and
in the `PUT` response.

**Statuses:** `NEW` -> `SHIPPED` or `NEW` -> `CANCELLED`. Both are final. A database trigger rejects
any other change with `409`.

#### `GET /api/orders`

Newest first, paged. All filters are optional and can be combined:

| Query param | Description |
|---|---|
| `status` | `NEW`, `SHIPPED` or `CANCELLED` |
| `customerId` | one customer's orders |
| `supplierId` | orders that contain at least one of this vendor's products |
| `page`, `size` | paging (default `0`, `20`) |

```http
GET /api/orders?supplierId=1&status=NEW&size=10
```

Response: a page of orders (without items).

#### `GET /api/orders/{id}`

The order with its items. `404` if missing.

#### `POST /api/orders`

Creates the order and its lines in one transaction (see [Transactions](#transactions-and-locking)).

| Field | Type | Required | Rules |
|---|---|---|---|
| `customerId` | integer | yes | must exist |
| `orderDate` | date-time | yes | |
| `deliveryDate` | date-time | no | not before `orderDate` (else `400`) |
| `shippingAddressId` | integer | yes | must exist **and belong to the customer** |
| `status` | string | no | `NEW` (default), `SHIPPED`, `CANCELLED` |
| `orderItems` | array | yes, ≥ 1 | each: `productId` (required, must exist, at most once per order), `quantity` (required, > 0), `unitPrice` (optional, defaults to the product's current price) |

```json
{
  "customerId": 1,
  "orderDate": "2026-09-11T10:00:00",
  "shippingAddressId": 1,
  "orderItems": [
    { "productId": 12, "quantity": 3 },
    { "productId": 2, "quantity": 1, "unitPrice": 29.90 }
  ]
}
```

Response: `201` + the order with its items. Errors: `400` for validation, an unknown customer,
product or address, another customer's address, or a duplicate product line. `409` if a quantity
exceeds the stock (`"Insufficient stock for product 12: 3 available, 5 requested"`).

The stock of each product is reduced by a trigger.

#### `PUT /api/orders/{id}`

Partial update: only the fields that are present are changed.

| Field | Notes |
|---|---|
| `customerId`, `shippingAddressId` | must exist |
| `orderDate`, `deliveryDate` | date-times, `deliveryDate` not before `orderDate` (else `400`) |
| `status` | `NEW`, `SHIPPED`, `CANCELLED`, but only `NEW -> SHIPPED / CANCELLED` is accepted (else `409`) |

```json
{ "deliveryDate": "2026-09-14T12:00:00", "status": "SHIPPED" }
```

Response: the updated order (without items). Database effects:

- every status change is written to `order_status_log` (audit trigger);
- `CANCELLED` puts the order's items back in stock (restock trigger).

#### `DELETE /api/orders/{id}`

Deletes the order, its items and its status-log rows. If the order was still `NEW`, the reserved
stock is released by a trigger.

```json
{ "message": "Order deleted successfully", "id": 200001 }
```

---

### Customers

```json
{ "id": 1, "firstName": "Dustin", "lastName": "Carey", "email": "vsmith1@example.com", "phone": "001-789-824-7188x591" }
```

| Endpoint | Request | Response |
|---|---|---|
| `GET /api/customers` | `?email=` (optional, exact), `page`, `size` | page of customers, sorted by id |
| `GET /api/customers/{id}` | | customer, `404` if missing |
| `POST /api/customers` | `firstName`, `lastName` (required, max 100), `email` (required, valid e-mail), `phone` (optional, max 30) | `201` + customer |
| `PUT /api/customers/{id}` | same as POST | updated customer |
| `DELETE /api/customers/{id}` | | delete message, `409` if the customer has orders or addresses |

```http
POST /api/customers
{ "firstName": "Matti", "lastName": "Meikäläinen", "email": "matti@example.com", "phone": "0401234567" }
```

#### Customer addresses

```json
{ "id": 1, "streetAddress": "39650 Harrington Plains Suite 474", "postalCode": "95942", "city": "Larryview", "country": "Guadeloupe" }
```

| Endpoint | Request | Response |
|---|---|---|
| `GET /api/customers/{id}/addresses` | | list of the customer's addresses |
| `GET /api/customers/{id}/addresses/{addressId}` | | address, `404` if it isn't this customer's |
| `POST /api/customers/{id}/addresses` | `streetAddress`, `city` (required), `postalCode`, `country` | `201` + address |
| `PUT /api/customers/{id}/addresses/{addressId}` | same as POST | updated address |
| `DELETE /api/customers/{id}/addresses/{addressId}` | | delete message, `409` if orders ship to it |

---

## Database features

Everything in this section is created by one script, [`db/vendor_api.sql`](db/vendor_api.sql). Some features work behind
the CRUD endpoints (indexes, triggers, the order transaction, users). Others are for direct SQL and
reporting (views, the stored procedure, the event).

| Feature | Used through the API | Used directly in the database |
|---|---|---|
| Indexes | ✔ list filters, sorting, lookups | ✔ |
| Foreign keys | ✔ -> `409` | ✔ |
| Triggers | ✔ order create / update / delete | ✔ any client |
| Transaction + row locks | ✔ `POST /api/orders` | ✔ `cancel_order` procedure |
| Status history (log table) | ✔ written on every status change | ✔ queried with SQL |
| Views | | ✔ `vendor_report` reporting account |
| `cancel_order` procedure | | ✔ `CALL cancel_order(id)` (the API cancels with `PUT`; the same triggers run) |
| Scheduled event | | ✔ daily purge of old log rows |
| Users and privileges | ✔ the API connects as `vendor_api` | ✔ |

### Schema

The course schema, plus one audit log table:

```
customers 1──* customeraddresses
customers 1──* orders *──1 customeraddresses (shipping address)
orders    1──* orderitems *──1 products *──1 productcategories
                                     products *──1 suppliers 1──1 supplieraddresses
orders    1──* order_status_log   (added: status history, filled by a trigger)
```

`order_status_log`'s foreign key has `ON DELETE CASCADE`, so deleting an order also deletes its log
rows.

Sample data: 100 000 customers and addresses, 200 000 orders, ~1.1 million order lines,
1 000 products, 100 suppliers, 10 categories.

### Indexes

The added indexes are for fields
that the API's repeated queries use in `WHERE` (search conditions) and `ORDER BY` (sorting):

| Index | Columns | Query it serves |
|---|---|---|
| `idx_orders_status_date` | `orders(status, order_date)` | `GET /api/orders?status=`: `WHERE status = ? ORDER BY order_date`. The search field comes first and the sort field second, so the matching index records are already in date order. |
| `idx_orders_customer_date` | `orders(customer_id, order_date)` | `GET /api/orders?customerId=`: one customer's orders, newest first |
| `idx_products_supplier_name` | `products(supplier_id, name)` | `GET /api/products?supplierId=`: `WHERE supplier_id = ? ORDER BY name` |
| `idx_products_name` | `products(name)` | `GET /api/products`: `ORDER BY name` |
| `idx_customers_email` | `customers(email)` | `GET /api/customers?email=`: exact lookup among 100 000 customers |
| `idx_statuslog_order_changed` | `order_status_log(order_id, changed_at)` | one order's status history, `WHERE order_id = ? ORDER BY changed_at` |
| `idx_statuslog_changed` | `order_status_log(changed_at)` | the purge event: `WHERE changed_at < ?` |


Check the query plan with `EXPLAIN`. The `key` column shows the index used:

```sql
EXPLAIN SELECT * FROM orders WHERE status = 'NEW' ORDER BY order_date DESC;
```

### Triggers

The business rules hold no matter who writes to the database: the API, HeidiSQL or a script.

| Trigger | When | What it does |
|---|---|---|
| `trigger_order_status_change` | `AFTER UPDATE ON orders` | Writes every status change (old -> new) with `NOW()` to `order_status_log` |
| `trigger_order_status_transition` | `BEFORE UPDATE ON orders` | Only allows `NEW -> SHIPPED` and `NEW -> CANCELLED`. Anything else is rejected with `SIGNAL SQLSTATE '45000'`. |
| `trigger_orderitem_check_stock` | `BEFORE INSERT ON orderitems` | Rejects an order line larger than the product's stock (`SIGNAL`) |
| `trigger_orderitem_reserve_stock` | `AFTER INSERT ON orderitems` | Reduces the product's stock by the line's quantity |
| `trigger_orderitem_release_stock` | `AFTER DELETE ON orderitems` | Returns the stock when a line of a `NEW` order is deleted. Shipped goods have left the warehouse, and cancelled orders were already restocked. |
| `trigger_order_cancel_restock` | `AFTER UPDATE ON orders` | When an order becomes `CANCELLED`, adds the quantities of its lines back to stock (one `UPDATE` with sub-queries) |

Errors raised with `SIGNAL SQLSTATE '45000'` reach the API client as `409 Conflict`, with the
trigger's message as the `detail`.

How it looks through the CRUD API (`test.sh` checks every step):

1. `POST /api/orders` with 3 units: the product's stock goes 20 -> 17 (reserve trigger).
2. `PUT /api/orders/{id}` with `{"status":"CANCELLED"}`: stock goes back to 20 (restock trigger),
   and a `NEW -> CANCELLED` row lands in `order_status_log` (log trigger).
3. `PUT /api/orders/{id}` with `{"status":"SHIPPED"}` on the cancelled order: `409`
   (transition trigger).

### Transactions and locking

**Creating an order** (`OrderService.createOrder`, `@Transactional`) is one transaction. It is done
completely or not at all (atomicity):

1. Validate the customer, and that the shipping address belongs to that customer.
2. Lock the ordered products with `SELECT … FOR UPDATE` (`ProductRepository.findAllByIdForUpdate`).
   Every transaction locks them in the same order (by id), which avoids deadlocks between two orders
   for the same products.
3. Check the stock against the locked rows. A concurrent order for the same product waits for the
   lock, so two customers can't both buy the last unit (no lost update).
4. Insert the order and its lines; the triggers reserve the stock.

If any step fails (validation, a trigger's `SIGNAL`), the whole transaction is rolled back. The stock
check exists twice on purpose: the Java check gives a clear error message, and the trigger protects
writes that don't go through the API. Order updates and deletes are also single transactions. The
isolation level is MariaDB's default `REPEATABLE READ`.

**The `cancel_order` stored procedure** shows the same idea in SQL:

```sql
CREATE PROCEDURE cancel_order (IN p_order_id INT)
BEGIN
    START TRANSACTION;
    SELECT id, status FROM orders WHERE id = p_order_id FOR UPDATE;
    UPDATE orders SET status = 'CANCELLED'
    WHERE id = p_order_id AND status = 'NEW';
    COMMIT;
END
```

```sql
CALL cancel_order(200001);
```

The `SELECT … FOR UPDATE` keeps another transaction from shipping or cancelling the same order at the
same time. Only a `NEW` order is changed. The API cancels with `PUT /api/orders/{id}`
`{"status":"CANCELLED"}`, which runs the same triggers.

### Views

Reporting views that combine tables. The read-only `vendor_report` account can read these but not
the base tables ([Security](#security)).

| View | Algorithm | What it combines |
|---|---|---|
| `product_catalog` | `MERGE` | `products` + `productcategories` + `suppliers`: products with category and supplier **names** |
| `order_summary` | `TEMPTABLE` | `orders` + `customers` + `orderitems`: one row per order with customer name, number of lines, units and total |
| `product_sales` | `TEMPTABLE` | `orderitems` + `orders` + `products`: units sold, number of orders and revenue per product, cancelled orders excluded |

- `MERGE`: a query on the view is converted into a query on the physical tables, so it uses their
  indexes.
- `TEMPTABLE`: the view is computed into a temporary table. The aggregate views (`GROUP BY`) are
  computed this way anyway, since they can't be merged. They are also not updatable.

```sql
SELECT * FROM product_catalog WHERE supplier_name = 'Polar Electronics Oy';
SELECT * FROM order_summary WHERE customer_id = 1 ORDER BY order_date DESC;
SELECT * FROM product_sales ORDER BY units_sold DESC;
```

### Order status history

The time dimension of the orders is modelled through the schema: `order_status_log` stores every
status change with the time it happened (transaction time, written with `NOW()` by the trigger).
From it you can see when an order was created, shipped or cancelled, and which status it had at any
moment:

```sql
SELECT old_status, new_status, changed_at
FROM order_status_log
WHERE order_id = 200001
ORDER BY changed_at;
```

The log is append-only from the application's point of view: rows are only added by the trigger.
They are removed only with their order (`ON DELETE CASCADE`) or by the purge event after two years.

### Scheduled event

`event_purge_order_status_log` runs once a day and deletes log rows older than two years
(`SUBTIME(NOW(), "730 0:0:0")`), so the log doesn't grow forever. It needs `event_scheduler=ON`.

### Security

| Measure | How |
|---|---|
| **Smallest necessary privileges** | `vendor_api` (the API) gets only `SELECT, INSERT, UPDATE, DELETE` on the database and `EXECUTE` on `cancel_order`. It has no right to change the structure, so even a bug or an injection could not drop tables. Schema changes are made with the `db/` script by an admin account (hence `ddl-auto=none`). |
| **Views and field-level rights** | `vendor_report` (read-only reporting) can `SELECT` the three views, and from `customers` only the fields `id, first_name, last_name`. It never sees customer e-mail addresses or phone numbers. |
| **No SQL injection** | All queries go through Spring Data JPA with bound parameters (derived queries, JPQL with `:named` parameters). No SQL text is built from user input. |
| **Input validation** | Every request body is a DTO with Bean Validation (`@NotBlank`, `@Email`, `@Positive`, `@Pattern` for statuses, `@Size` matching the column lengths) -> `400`. Page sizes are capped at 100. |
| **Integrity in the database** | Foreign keys and triggers enforce the rules for every client, not only the API. |
| **No leaking internals** | Unexpected database errors return `500 "Database error"` without SQL details. Only the triggers' own messages (`SQLSTATE 45000`) are shown. |
| **Secrets out of git** | `application.properties` is gitignored. The committed `application.properties.example` reads `DB_USER` / `DB_PASSWORD` from the environment. |


### Query techniques in the application

| Technique | Where |
|---|---|
| Spring Data derived queries | `findBySupplierIdOrderByNameAsc`, `findByCategoryIdOrderByNameAsc`, `findByEmail(email, Pageable)`, `findByCustomerIdOrderByIdAsc`, `findBySupplierId`, `findById_OrderId` |
| JPQL | `OrderRepository.search`: optional filters and an `EXISTS` subquery over order items -> products for `supplierId`, with a separate count query for paging. `deleteStatusLogsForOrder`: bulk `DELETE`. |
| Pessimistic locking | `ProductRepository.findAllByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)` -> `SELECT … FOR UPDATE`) |
| Paging and sorting | `PageRequest` + `Sort` (orders newest first, customers by id) |
| Cascades | `Order.items` with `CascadeType.ALL`: order lines are saved and deleted with their order |

### Avoiding the N+1 query problem

All `@ManyToOne` / `@OneToOne` associations are mapped with `fetch = FetchType.LAZY`
(JPA's default for these is EAGER, which made Hibernate run one extra SELECT per
related row, e.g. one per customer when listing orders).

Because `spring.jpa.open-in-view=false`, the JSON is written after the
persistence context is closed, so every association that ends up in a response
is loaded by the query itself with `@EntityGraph` (a LEFT JOIN in the same SELECT):

| Repository method | Fetched with the row |
|---|---|
| `OrderRepository.search` (GET /api/orders) | `customer`, `shippingAddress` |
| `OrderRepository.findById` (GET/PUT /api/orders/{id}) | `customer`, `shippingAddress` |
| `ProductRepository.findById` and the list finders | `category` |

Associations that are not serialized stay lazy and are never loaded:
`OrderItem.product` (the response only uses the product id, which Hibernate
reads from the proxy without a query), and the `@JsonIgnore`d back references
`CustomerAddress.customer` and `SupplierAddress.supplier`.


---

## Testing

| What | How |
|---|---|
| Database features | The end of [`db/vendor_api.sql`](db/vendor_api.sql) has example queries: `EXPLAIN` for the indexed queries, the views, the triggers inside a transaction that is rolled back (stock reservation, cancel/restock, the status log, a rejected status change) and `CALL cancel_order`. |
| API end to end | `./test.sh` runs 94 checks covering every endpoint, the error cases (400/404/409) and the database behaviour seen through CRUD: stock 20 -> 17 -> 12 -> 17, trigger-rejected status changes, foreign-key conflicts. It creates its own supplier, category, product, customer, addresses and orders and deletes them at the end, so the sample data stays the same. Options: `SHOW_RESPONSES=0`, `LIST_LIMIT=N`, `BASE_URL` (see the top of the script). Requires `curl` and `jq`. |

---

## Known limitations

- The stock triggers assume that stock reflects reservations **from the moment they were installed**.
  The sample data's existing orders never reserved stock, so cancelling an old `NEW` order adds its
  quantities to the current stock.
- The status history starts when `db/vendor_api.sql` is run; the sample data's earlier status
  changes aren't known.
- No authentication: every endpoint is open, and any client can act for any vendor
  (see [Security](#security)).
- `GET /api/products` returns all matching products without paging (1 000 in the sample data).
