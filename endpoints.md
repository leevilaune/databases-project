# Endpoints

Base URL: `http://localhost:8080/api`

General notes:

- Request and response bodies are JSON with **camelCase** field names (Spring/Jackson default).
- Date-time fields (`orderDate`, `deliveryDate`, `cutoff`) are ISO-8601 local date-times, e.g. `2026-09-11T14:30:00`.
- `POST` endpoints that create a row return **201 Created**; everything else returns **200 OK** on success.
- Errors:

| Status | When |
|---|---|
| 400 Bad Request | Validation failed, or a referenced row (customer, address, product, category, supplier) does not exist |
| 404 Not Found | The row in the URL (`/{id}`) does not exist |
| 409 Conflict | The operation would break a foreign key, e.g. deleting a product that is used in orders, or a category that still has products |


Testing: start the API (`mvn spring-boot:run`) and run `./test.sh` from the project root. It creates its own
category, product and order, checks every endpoint (including the 400/404/409 error cases) and deletes the
test rows again. See the top of `test.sh` for options (`BASE_URL`, `SHOW_RESPONSES=0`, `LIST_LIMIT`, `TEST_LIST_ORDERS=1`, ...). Responses are pretty-printed with `jq`, so it needs to be installed.

---

## Products

A product response looks like this. `category` is the full category object (or `null`), `supplierId` is the id of the supplier.

```json
{
  "id": 12,
  "name": "Wireless Keyboard",
  "description": "Compact wireless keyboard",
  "price": 39.90,
  "stockQuantity": 25,
  "category": {
    "id": 1,
    "name": "Computers",
    "description": "Computers and computer accessories"
  },
  "supplierId": 1
}
```

### CREATE product

**Endpoint**
```http
POST /api/products
```

**Input**

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | yes | not blank |
| `description` | string | no | |
| `price` | number | yes | `>= 0` |
| `stockQuantity` | integer | yes | `>= 0` |
| `categoryId` | integer | no | must exist in `productcategories` |
| `supplierId` | integer | no | must exist in `suppliers` |

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

**Output** — `201 Created`
```json
{
  "id": 12,
  "name": "Wireless Keyboard",
  "description": "Compact wireless keyboard",
  "price": 39.90,
  "stockQuantity": 25,
  "category": {
    "id": 1,
    "name": "Computers",
    "description": "Computers and computer accessories"
  },
  "supplierId": 1
}
```

---

### READ product

**Endpoint**
```http
GET /api/products/12
```

**Output**
```json
{
  "id": 12,
  "name": "Wireless Keyboard",
  "description": "Compact wireless keyboard",
  "price": 39.90,
  "stockQuantity": 25,
  "category": {
    "id": 1,
    "name": "Computers",
    "description": "Computers and computer accessories"
  },
  "supplierId": 1
}
```

---

### READ ALL products

**Endpoint**
```http
GET /api/products
```

**Output**
```json
[
  {
    "id": 1,
    "name": "Laptop",
    "description": "15-inch laptop",
    "price": 899.90,
    "stockQuantity": 10,
    "category": {
      "id": 1,
      "name": "Computers",
      "description": "Computers and computer accessories"
    },
    "supplierId": 1
  },
  {
    "id": 12,
    "name": "Wireless Keyboard",
    "description": "Compact wireless keyboard",
    "price": 39.90,
    "stockQuantity": 25,
    "category": {
      "id": 1,
      "name": "Computers",
      "description": "Computers and computer accessories"
    },
    "supplierId": 1
  }
]
```

---

### UPDATE product

Replaces all fields of the product (same input as CREATE).

**Endpoint**
```http
PUT /api/products/12
```

**Input**
```json
{
  "name": "Wireless Mechanical Keyboard",
  "description": "Compact mechanical wireless keyboard",
  "price": 59.90,
  "stockQuantity": 20,
  "categoryId": 1,
  "supplierId": 1
}
```

**Output**
```json
{
  "id": 12,
  "name": "Wireless Mechanical Keyboard",
  "description": "Compact mechanical wireless keyboard",
  "price": 59.90,
  "stockQuantity": 20,
  "category": {
    "id": 1,
    "name": "Computers",
    "description": "Computers and computer accessories"
  },
  "supplierId": 1
}
```

---

### DELETE product

Fails with `409 Conflict` if the product is used in any order.

**Endpoint**
```http
DELETE /api/products/12
```

**Output**
```json
{
  "message": "Product deleted successfully",
  "id": 12
}
```

---

## Orders

An order uses both the `orders` and `orderitems` tables. In responses, `customer` and `shippingAddress` are the full objects and each order item has a composite `id` (`orderId` + `productId`).

### CREATE order

**Endpoint**
```http
POST /api/orders
```

**Input**

| Field | Type | Required | Notes |
|---|---|---|---|
| `customerId` | integer | yes | must exist in `customers` |
| `orderDate` | date-time | yes | |
| `deliveryDate` | date-time | no | |
| `shippingAddressId` | integer | yes | must exist in `customeraddresses` |
| `status` | string | yes | e.g. `NEW`, `SHIPPED`, `DELIVERED`, `CANCELLED` |
| `orderItems` | array | no | each item: `productId`, `quantity`, `unitPrice` (all required); `productId` must exist |

```json
{
  "customerId": 1,
  "orderDate": "2026-09-11T10:00:00",
  "deliveryDate": null,
  "shippingAddressId": 1,
  "status": "NEW",
  "orderItems": [
    {
      "productId": 2,
      "quantity": 2,
      "unitPrice": 29.90
    },
    {
      "productId": 12,
      "quantity": 1,
      "unitPrice": 39.90
    }
  ]
}
```

**Output** — `201 Created`
```json
{
  "id": 200001,
  "customer": {
    "id": 1,
    "firstName": "Matti",
    "lastName": "Meikäläinen",
    "email": "matti@example.com",
    "phone": "+358401234567"
  },
  "orderDate": "2026-09-11T10:00:00",
  "deliveryDate": null,
  "shippingAddress": {
    "id": 1,
    "streetAddress": "Esimerkkikatu 1",
    "postalCode": "00100",
    "city": "Helsinki",
    "country": "Finland"
  },
  "status": "NEW",
  "orderItems": [
    {
      "id": { "orderId": 200001, "productId": 2 },
      "productId": 2,
      "quantity": 2,
      "unitPrice": 29.90
    },
    {
      "id": { "orderId": 200001, "productId": 12 },
      "productId": 12,
      "quantity": 1,
      "unitPrice": 39.90
    }
  ]
}
```

---

### READ order

Returns the order together with its order items.

**Endpoint**
```http
GET /api/orders/200001
```

**Output** — same shape as the CREATE output above.

---

### READ ALL orders

Returns all orders **without** their items (`orderItems` is `null`). Use `GET /api/orders/{id}` to get the items of one order.

**Endpoint**
```http
GET /api/orders
```

**Output**
```json
[
  {
    "id": 1,
    "customer": {
      "id": 51914,
      "firstName": "...",
      "lastName": "...",
      "email": "...",
      "phone": "..."
    },
    "orderDate": "2024-04-03T17:51:08",
    "deliveryDate": "2024-04-06T16:16:43",
    "shippingAddress": {
      "id": 51914,
      "streetAddress": "...",
      "postalCode": "...",
      "city": "...",
      "country": "..."
    },
    "status": "CANCELLED",
    "orderItems": null
  }
]
```

---

### UPDATE order

Partial update: only the fields that are present (non-null) are changed. Order items are not changed by this endpoint.

**Endpoint**
```http
PUT /api/orders/200001
```

**Input** — all fields optional

| Field | Type | Notes |
|---|---|---|
| `customerId` | integer | must exist in `customers` |
| `orderDate` | date-time | |
| `deliveryDate` | date-time | |
| `shippingAddressId` | integer | must exist in `customeraddresses` |
| `status` | string | |

```json
{
  "deliveryDate": "2026-09-14T12:00:00",
  "status": "SHIPPED"
}
```

**Output** — the updated order (without items)
```json
{
  "id": 200001,
  "customer": { "id": 1, "firstName": "Matti", "lastName": "Meikäläinen", "email": "matti@example.com", "phone": "+358401234567" },
  "orderDate": "2026-09-11T10:00:00",
  "deliveryDate": "2026-09-14T12:00:00",
  "shippingAddress": { "id": 1, "streetAddress": "Esimerkkikatu 1", "postalCode": "00100", "city": "Helsinki", "country": "Finland" },
  "status": "SHIPPED",
  "orderItems": null
}
```

Changing the order status also fires the `order_status_log` trigger in the database.

---

### DELETE order

Deletes the order and its order items.

**Endpoint**
```http
DELETE /api/orders/200001
```

**Output**
```json
{
  "message": "Order deleted successfully",
  "id": 200001
}
```

---

### Cancel old orders (Criteria API)

Sets `status = 'CANCELLED'` on every order that still has status `NEW` and an `orderDate` before `cutoff`. Implemented with a JPA `CriteriaUpdate`.

**Endpoint**
```http
POST /api/orders/cancel-old?cutoff=2024-01-01T00:00:00
```

**Output** — number of cancelled orders
```json
1532
```

---

### Delete old status logs (JPQL)

Deletes rows from `order_status_log` whose `changed_at` is before `cutoff`. Implemented with a JPQL `DELETE` query in `OrderRepository`.

**Endpoint**
```http
DELETE /api/orders/delete-logs?cutoff=2025-01-01T00:00:00
```

**Output** — number of deleted log rows
```json
87
```

---

## Product categories

### CREATE productcategory

**Endpoint**
```http
POST /api/productcategories
```

**Input**

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | yes | not blank, max 100 characters |
| `description` | string | no | |

```json
{
  "name": "Electronics",
  "description": "Electronic devices and accessories"
}
```

**Output** — `201 Created`
```json
{
  "id": 5,
  "name": "Electronics",
  "description": "Electronic devices and accessories"
}
```

---

### READ ALL productcategories

**Endpoint**
```http
GET /api/productcategories
```

**Output**
```json
[
  {
    "id": 1,
    "name": "Computers",
    "description": "Computers and computer accessories"
  },
  {
    "id": 5,
    "name": "Electronics",
    "description": "Electronic devices and accessories"
  }
]
```

---

### READ productcategory

**Endpoint**
```http
GET /api/productcategories/5
```

**Output**
```json
{
  "id": 5,
  "name": "Electronics",
  "description": "Electronic devices and accessories"
}
```

---

### UPDATE productcategory

Replaces all fields of the category (same input as CREATE).

**Endpoint**
```http
PUT /api/productcategories/5
```

**Input**
```json
{
  "name": "Consumer Electronics",
  "description": "Electronic devices, accessories and related products"
}
```

**Output**
```json
{
  "id": 5,
  "name": "Consumer Electronics",
  "description": "Electronic devices, accessories and related products"
}
```

---

### DELETE productcategory

Fails with `409 Conflict` if any product still belongs to the category.

**Endpoint**
```http
DELETE /api/productcategories/5
```

**Output**
```json
{
  "message": "Product category deleted successfully",
  "id": 5
}
```

---

## Endpoint overview

| Endpoint | Method | Example |
|---|---|---|
| CREATE product | POST | `/api/products` |
| READ product | GET | `/api/products/12` |
| READ ALL products | GET | `/api/products` |
| UPDATE product | PUT | `/api/products/12` |
| DELETE product | DELETE | `/api/products/12` |
| CREATE order | POST | `/api/orders` |
| READ order | GET | `/api/orders/200001` |
| READ ALL orders | GET | `/api/orders` |
| UPDATE order | PUT | `/api/orders/200001` |
| DELETE order | DELETE | `/api/orders/200001` |
| Cancel old orders | POST | `/api/orders/cancel-old?cutoff=2024-01-01T00:00:00` |
| Delete old status logs | DELETE | `/api/orders/delete-logs?cutoff=2025-01-01T00:00:00` |
| CREATE productcategory | POST | `/api/productcategories` |
| READ ALL productcategories | GET | `/api/productcategories` |
| READ productcategory | GET | `/api/productcategories/5` |
| UPDATE productcategory | PUT | `/api/productcategories/5` |
| DELETE productcategory | DELETE | `/api/productcategories/5` |


## Example classes

Template for adding a new endpoint (entity → repository → service → controller).
Request bodies should use a DTO (see `product/dto/ProductRequest.java`) and delete endpoints
return `common.DeleteResponse`.

### Entity
```java
package dev.onesnzeroes.vendor.endpoint.example;

import jakarta.persistence.*;

@Entity
@Table(name = "examples")
public class Example {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "example", nullable = false)
    private String example;

    public Example() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getExample() {
        return this.example;
    }

    public void setExample(String example) {
        this.example = example;
    }
}
```

### Repository
```java
package dev.onesnzeroes.vendor.endpoint.example;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExampleRepository extends JpaRepository<Example, Integer> {

}
```

### Service
```java
package dev.onesnzeroes.vendor.endpoint.example;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ExampleService {

    private final ExampleRepository exampleRepository;

    public ExampleService(ExampleRepository exampleRepository) {
        this.exampleRepository = exampleRepository;
    }

    public List<Example> getAllExamples() {
        return exampleRepository.findAll();
    }

    public Example getExampleById(Integer id) {
        return exampleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Example not found"));
    }

    public Example createExample(Example example) {
        return exampleRepository.save(example);
    }

    public void deleteExample(Integer id) {
        exampleRepository.delete(getExampleById(id));
    }
}
```

### Controller
```java
package dev.onesnzeroes.vendor.endpoint.example;

import dev.onesnzeroes.vendor.endpoint.common.DeleteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/examples")
public class ExampleController {

    private final ExampleService exampleService;

    public ExampleController(ExampleService exampleService) {
        this.exampleService = exampleService;
    }

    @GetMapping
    public List<Example> getAllExamples() {
        return this.exampleService.getAllExamples();
    }

    @GetMapping("/{id}")
    public Example getExample(@PathVariable Integer id) {
        return this.exampleService.getExampleById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Example createExample(@RequestBody Example example) {
        return this.exampleService.createExample(example);
    }

    @DeleteMapping("/{id}")
    public DeleteResponse deleteExample(@PathVariable Integer id) {
        exampleService.deleteExample(id);
        return new DeleteResponse("Example deleted successfully", id);
    }
}
```
