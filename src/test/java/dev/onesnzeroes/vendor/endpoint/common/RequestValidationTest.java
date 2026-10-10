package dev.onesnzeroes.vendor.endpoint.common;

import dev.onesnzeroes.vendor.endpoint.address.dto.AddressRequest;
import dev.onesnzeroes.vendor.endpoint.customer.dto.CustomerRequest;
import dev.onesnzeroes.vendor.endpoint.order.dto.CreateOrderRequest;
import dev.onesnzeroes.vendor.endpoint.order.dto.UpdateOrderRequest;
import dev.onesnzeroes.vendor.endpoint.orderitem.dto.CreateOrderItemRequest;
import dev.onesnzeroes.vendor.endpoint.product.dto.ProductRequest;
import dev.onesnzeroes.vendor.endpoint.productcategory.dto.ProductCategoryRequest;
import dev.onesnzeroes.vendor.endpoint.supplier.dto.SupplierRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> invalidFields(Object request) {
        Set<String> fields = new TreeSet<>();
        for (ConstraintViolation<Object> violation : validator.validate(request)) {
            fields.add(violation.getPropertyPath().toString());
        }
        return fields;
    }

    private static SupplierRequest supplier() {
        SupplierRequest request = new SupplierRequest();
        request.setName("Test Vendor");
        request.setContactName("Maija");
        request.setPhone("0401234567");
        request.setEmail("vendor@example.com");
        return request;
    }

    private static CustomerRequest customer() {
        CustomerRequest request = new CustomerRequest();
        request.setFirstName("Testi");
        request.setLastName("Asiakas");
        request.setEmail("testi@example.com");
        request.setPhone("0401234567");
        return request;
    }

    private static AddressRequest address() {
        AddressRequest request = new AddressRequest();
        request.setStreetAddress("Testikatu 1");
        request.setPostalCode("00100");
        request.setCity("Helsinki");
        request.setCountry("Finland");
        return request;
    }

    private static ProductRequest product() {
        ProductRequest request = new ProductRequest();
        request.setName("Test Product");
        request.setPrice(new BigDecimal("49.90"));
        request.setStockQuantity(10);
        return request;
    }

    private static CreateOrderRequest order() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(12);
        request.setOrderDate(LocalDateTime.of(2026, 10, 1, 10, 0));
        request.setShippingAddressId(30);
        request.setOrderItems(List.of(new CreateOrderItemRequest(8, 1, null)));
        return request;
    }

    @Test
    void validRequestsHaveNoViolations() {
        assertEquals(Set.of(), invalidFields(supplier()));
        assertEquals(Set.of(), invalidFields(customer()));
        assertEquals(Set.of(), invalidFields(address()));
        assertEquals(Set.of(), invalidFields(product()));
        assertEquals(Set.of(), invalidFields(order()));
        assertEquals(Set.of(), invalidFields(new UpdateOrderRequest()));
    }

    @Test
    void supplierNeedsANameAndAValidEmail() {
        SupplierRequest request = supplier();
        request.setName(" ");
        request.setEmail("not-an-email");

        assertEquals(Set.of("email", "name"), invalidFields(request));
    }

    @Test
    void supplierEmailIsOptional() {
        SupplierRequest request = supplier();
        request.setEmail(null);

        assertEquals(Set.of(), invalidFields(request));
    }

    @Test
    void supplierFieldLengthsAreLimited() {
        SupplierRequest request = supplier();
        request.setName("x".repeat(256));
        request.setContactName("x".repeat(101));
        request.setPhone("1".repeat(31));

        assertEquals(Set.of("contactName", "name", "phone"), invalidFields(request));
    }

    @Test
    void customerNeedsNamesAndAValidEmail() {
        CustomerRequest request = new CustomerRequest();
        assertEquals(Set.of("email", "firstName", "lastName"), invalidFields(request));

        CustomerRequest badEmail = customer();
        badEmail.setEmail("not-an-email");
        assertEquals(Set.of("email"), invalidFields(badEmail));
    }

    @Test
    void addressNeedsStreetAndCity() {
        AddressRequest request = new AddressRequest();
        assertEquals(Set.of("city", "streetAddress"), invalidFields(request));

        AddressRequest tooLong = address();
        tooLong.setPostalCode("1".repeat(21));
        assertEquals(Set.of("postalCode"), invalidFields(tooLong));
    }

    @Test
    void productCategoryNeedsAName() {
        ProductCategoryRequest request = new ProductCategoryRequest();
        assertEquals(Set.of("name"), invalidFields(request));

        request.setName("x".repeat(101));
        assertEquals(Set.of("name"), invalidFields(request));
    }

    @Test
    void productPriceAndStockCannotBeNegative() {
        ProductRequest request = product();
        request.setPrice(new BigDecimal("-0.01"));
        request.setStockQuantity(-1);

        assertEquals(Set.of("price", "stockQuantity"), invalidFields(request));
    }

    @Test
    void productAllowsZeroPriceAndStock() {
        ProductRequest request = product();
        request.setPrice(BigDecimal.ZERO);
        request.setStockQuantity(0);

        assertEquals(Set.of(), invalidFields(request));
    }

    @Test
    void orderNeedsCustomerDateAddressAndItems() {
        CreateOrderRequest request = new CreateOrderRequest();

        assertEquals(Set.of("customerId", "orderDate", "orderItems", "shippingAddressId"), invalidFields(request));
    }

    @Test
    void orderItemsAreValidatedOneByOne() {
        CreateOrderRequest request = order();
        request.setOrderItems(List.of(
                new CreateOrderItemRequest(8, 1, null),
                new CreateOrderItemRequest(null, 0, -1.0)));

        assertEquals(Set.of("orderItems[1].productId", "orderItems[1].quantity", "orderItems[1].unitPrice"),
                invalidFields(request));
    }

    @Test
    void orderStatusMustBeKnown() {
        CreateOrderRequest create = order();
        create.setStatus("PENDING");
        assertEquals(Set.of("status"), invalidFields(create));

        UpdateOrderRequest update = new UpdateOrderRequest();
        update.setStatus("LOST");
        assertEquals(Set.of("status"), invalidFields(update));

        for (String status : List.of("NEW", "SHIPPED", "CANCELLED")) {
            update.setStatus(status);
            assertEquals(Set.of(), invalidFields(update));
        }
    }
}
