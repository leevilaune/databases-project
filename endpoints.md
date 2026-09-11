# Endpoints

## Product

### CREATE product

**Endpoint**
```http
POST /products
```

**Input**
```json
{
  "name": "Wireless Keyboard",
  "description": "Compact wireless keyboard",
  "price": 39.90,
  "stock_quantity": 25,
  "category_id": 1,
  "supplier_id": 1
}
```

**Output**
```json
{
  "id": 12,
  "name": "Wireless Keyboard",
  "description": "Compact wireless keyboard",
  "price": 39.90,
  "stock_quantity": 25,
  "category_id": 1,
  "supplier_id": 1
}
```

---

### READ product

**Endpoint**
```http
GET /products/12
```

**Output**
```json
{
  "id": 12,
  "name": "Wireless Keyboard",
  "description": "Compact wireless keyboard",
  "price": 39.90,
  "stock_quantity": 25,
  "category_id": 1,
  "supplier_id": 1
}
```

---

### READ ALL product

**Endpoint**
```http
GET /products
```

**Output**
```json
[
  {
    "id": 1,
    "name": "Laptop",
    "description": "15-inch laptop",
    "price": 899.90,
    "stock_quantity": 10,
    "category_id": 1,
    "supplier_id": 1
  },
  {
    "id": 2,
    "name": "Wireless Mouse",
    "description": "Bluetooth wireless mouse",
    "price": 29.90,
    "stock_quantity": 45,
    "category_id": 1,
    "supplier_id": 2
  },
  {
    "id": 12,
    "name": "Wireless Keyboard",
    "description": "Compact wireless keyboard",
    "price": 39.90,
    "stock_quantity": 25,
    "category_id": 1,
    "supplier_id": 1
  }
]
```

---

### UPDATE product

**Endpoint**
```http
PUT /products/12
```

**Input**
```json
{
  "name": "Wireless Mechanical Keyboard",
  "description": "Compact mechanical wireless keyboard",
  "price": 59.90,
  "stock_quantity": 20,
  "category_id": 1,
  "supplier_id": 1
}
```

**Output**
```json
{
  "id": 12,
  "name": "Wireless Mechanical Keyboard",
  "description": "Compact mechanical wireless keyboard",
  "price": 59.90,
  "stock_quantity": 20,
  "category_id": 1,
  "supplier_id": 1
}
```

---

### DELETE product

**Endpoint**
```http
DELETE /products/12
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

### CREATE order

The order uses both the `orders` and `orderitems` tables.

**Endpoint**
```http
POST /orders
```

**Input**
```json
{
  "customer_id": 1,
  "order_date": "2026-09-11",
  "delivery_date": null,
  "shipping_address_id": 1,
  "status": "PENDING",
  "items": [
    {
      "product_id": 2,
      "quantity": 2,
      "unit_price": 29.90
    },
    {
      "product_id": 12,
      "quantity": 1,
      "unit_price": 39.90
    }
  ]
}
```

**Output**
```json
{
  "id": 101,
  "customer_id": 1,
  "order_date": "2026-09-11",
  "delivery_date": null,
  "shipping_address_id": 1,
  "status": "PENDING",
  "items": [
    {
      "product_id": 2,
      "quantity": 2,
      "unit_price": 29.90
    },
    {
      "product_id": 12,
      "quantity": 1,
      "unit_price": 39.90
    }
  ]
}
```

---

### READ order

**Endpoint**
```http
GET /orders/101
```

**Output**
```json
{
  "id": 101,
  "customer_id": 1,
  "order_date": "2026-09-11",
  "delivery_date": null,
  "shipping_address_id": 1,
  "status": "PENDING",
  "items": [
    {
      "product_id": 2,
      "quantity": 2,
      "unit_price": 29.90
    },
    {
      "product_id": 12,
      "quantity": 1,
      "unit_price": 39.90
    }
  ]
}
```

---

### READ ALL orders

**Endpoint**
```http
GET /orders
```

**Output**
```json
[
  {
    "id": 100,
    "customer_id": 2,
    "order_date": "2026-09-08",
    "delivery_date": "2026-09-10",
    "shipping_address_id": 2,
    "status": "DELIVERED"
  },
  {
    "id": 101,
    "customer_id": 1,
    "order_date": "2026-09-11",
    "delivery_date": null,
    "shipping_address_id": 1,
    "status": "PENDING"
  }
]
```

---

### UPDATE order

**Endpoint**
```http
PUT /orders/101
```

**Input**
```json
{
  "delivery_date": "2026-09-14",
  "status": "SHIPPED"
}
```

**Output**
```json
{
  "id": 101,
  "customer_id": 1,
  "order_date": "2026-09-11",
  "delivery_date": "2026-09-14",
  "shipping_address_id": 1,
  "status": "SHIPPED"
}
```

Changing the order status can also trigger the previously implemented `order_status_log` trigger.

---

### DELETE order

**Endpoint**
```http
DELETE /orders/101
```

**Output**
```json
{
  "message": "Order deleted successfully",
  "id": 101
}
```

---

## Product categories

### CREATE productcategory

**Endpoint**
```http
POST /productcategories
```

**Input**
```json
{
  "name": "Electronics",
  "description": "Electronic devices and accessories"
}
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

### READ ALL productcategory

**Endpoint**
```http
GET /productcategories
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
    "id": 2,
    "name": "Office",
    "description": "Office equipment and supplies"
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
GET /productcategories/5
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

**Endpoint**
```http
PUT /productcategories/5
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

**Endpoint**
```http
DELETE /productcategories/5
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
| CREATE product | POST | `/products` |
| READ product | GET | `/products/12` |
| READ ALL product | GET | `/products` |
| UPDATE product | PUT | `/products/12` |
| DELETE product | DELETE | `/products/12` |
| CREATE order | POST | `/orders` |
| READ order | GET | `/orders/101` |
| READ ALL orders | GET | `/orders` |
| UPDATE order | PUT | `/orders/101` |
| DELETE order | DELETE | `/orders/101` |
| CREATE productcategory | POST | `/productcategories` |
| READ ALL productcategory | GET | `/productcategories` |
| READ productcategory | GET | `/productcategories/5` |
| UPDATE productcategory | PUT | `/productcategories/5` |
| DELETE productcategory | DELETE | `/productcategories/5` |


## Example classes

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

import org.springframework.stereotype.Service;

import java.util.List;

@Service

public class ExampleService {

    private final ExampleRepository exampleRepository;

    public ExampleRepository(OrderRepository exampleRepository) {
        this.exampleRepository = exampleRepository;
    }

    public List<Example> getAllExamples() {
        return exampleRepository.findAll();
    }

    public Example getExampleById(Integer id) {
        return exampleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Example not found"));

    }

    public Example createExample(Example example) {
        return exampleRepository.save(example);
    }

    public void deleteExample(Integer id) {
        exampleRepository.deleteById(id);
    }
}
```

### Controller
```java
package dev.onesnzeroes.vendor.endpoint.example;

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
    public Order getExample(@PathVariable Integer id) {
        return this.exampleService.getExampleById(id);
    }

    @PostMapping
    public Example createExample(@RequestBody Example example) {
        return this.exampleService.createExample(example);
    }

    @DeleteMapping("/{id}")
    public void deleteExample(@PathVariable Integer id) {
        exampleService.deleteExample(id);
    }
}
```