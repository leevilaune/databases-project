package dev.onesnzeroes.vendor;

import dev.onesnzeroes.vendor.endpoint.address.CustomerAddress;
import dev.onesnzeroes.vendor.endpoint.address.SupplierAddress;
import dev.onesnzeroes.vendor.endpoint.customer.Customer;
import dev.onesnzeroes.vendor.endpoint.product.Product;
import dev.onesnzeroes.vendor.endpoint.productcategory.ProductCategory;
import dev.onesnzeroes.vendor.endpoint.supplier.Supplier;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TestData {

    private TestData() {
    }

    public static Supplier supplier(int id, String name) {
        Supplier supplier = new Supplier();
        supplier.setId(id);
        supplier.setName(name);
        supplier.setContactName("Contact");
        supplier.setPhone("0401234567");
        supplier.setEmail("vendor@example.com");
        return supplier;
    }

    public static SupplierAddress supplierAddress(int id, Supplier supplier, String street, String city) {
        SupplierAddress address = new SupplierAddress();
        address.setId(id);
        address.setSupplier(supplier);
        address.setStreetAddress(street);
        address.setPostalCode("00100");
        address.setCity(city);
        address.setCountry("Finland");
        return address;
    }

    public static ProductCategory category(int id, String name) {
        ProductCategory category = new ProductCategory();
        category.setId(id);
        category.setName(name);
        category.setDescription("Description of " + name);
        return category;
    }

    public static Product product(int id, String name, String price, int stock,
                                  ProductCategory category, Integer supplierId) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setDescription("Description of " + name);
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stock);
        product.setCategory(category);
        product.setSupplierId(supplierId);
        return product;
    }

    public static Customer customer(int id, String firstName, String lastName, String email) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setEmail(email);
        customer.setPhone("0401234567");
        return customer;
    }

    public static CustomerAddress customerAddress(int id, Customer customer, String street, String city) {
        CustomerAddress address = new CustomerAddress();
        address.setId(id);
        address.setCustomer(customer);
        address.setStreetAddress(street);
        address.setPostalCode("00100");
        address.setCity(city);
        address.setCountry("Finland");
        return address;
    }

    public static DataIntegrityViolationException foreignKeyViolation() {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(
                        "Cannot delete or update a parent row: a foreign key constraint fails"));
    }

    public static UncategorizedSQLException triggerError(String message) {
        return new UncategorizedSQLException("trigger", "UPDATE orders",
                new SQLException("(conn=42) " + message, "45000"));
    }

    public static ResponseStatusException assertStatus(HttpStatus status, String reason, Executable call) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, call);
        assertEquals(status, ex.getStatusCode());
        if (reason != null) {
            assertEquals(reason, ex.getReason());
        }
        return ex;
    }
}
