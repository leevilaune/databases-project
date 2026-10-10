package dev.onesnzeroes.vendor.endpoint.order;

import dev.onesnzeroes.vendor.TestData;
import dev.onesnzeroes.vendor.endpoint.customer.Customer;
import dev.onesnzeroes.vendor.endpoint.order.dto.CreateOrderRequest;
import dev.onesnzeroes.vendor.endpoint.order.dto.OrderResponse;
import dev.onesnzeroes.vendor.endpoint.order.dto.UpdateOrderRequest;
import dev.onesnzeroes.vendor.endpoint.orderitem.OrderItem;
import dev.onesnzeroes.vendor.endpoint.orderitem.OrderItemId;
import dev.onesnzeroes.vendor.endpoint.orderitem.OrderItemMapper;
import dev.onesnzeroes.vendor.endpoint.orderitem.dto.OrderItemResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderMapperTest {

    private static final LocalDateTime ORDER_DATE = LocalDateTime.of(2026, 10, 1, 10, 0);

    private final OrderMapper mapper = new OrderMapper();

    @Test
    void toEntityDefaultsTheStatusToNew() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderDate(ORDER_DATE);

        Order order = mapper.toEntity(request);

        assertEquals("NEW", order.getStatus());
        assertEquals(ORDER_DATE, order.getOrderDate());
        assertNull(order.getDeliveryDate());
        assertNull(order.getCustomer());
        assertTrue(order.getItems().isEmpty());
    }

    @Test
    void toEntityKeepsAGivenStatusAndDeliveryDate() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderDate(ORDER_DATE);
        request.setDeliveryDate(ORDER_DATE.plusDays(1));
        request.setStatus("CANCELLED");

        Order order = mapper.toEntity(request);

        assertEquals("CANCELLED", order.getStatus());
        assertEquals(ORDER_DATE.plusDays(1), order.getDeliveryDate());
    }

    @Test
    void updateEntityIgnoresNullFields() {
        Order order = new Order();
        order.setOrderDate(ORDER_DATE);
        order.setStatus("NEW");

        mapper.updateEntity(order, new UpdateOrderRequest());

        assertEquals(ORDER_DATE, order.getOrderDate());
        assertEquals("NEW", order.getStatus());
        assertNull(order.getDeliveryDate());
    }

    @Test
    void updateEntityCopiesGivenFields() {
        Order order = new Order();
        order.setOrderDate(ORDER_DATE);
        order.setStatus("NEW");
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setOrderDate(ORDER_DATE.minusDays(1));
        request.setDeliveryDate(ORDER_DATE.plusDays(3));
        request.setStatus("SHIPPED");

        mapper.updateEntity(order, request);

        assertEquals(ORDER_DATE.minusDays(1), order.getOrderDate());
        assertEquals(ORDER_DATE.plusDays(3), order.getDeliveryDate());
        assertEquals("SHIPPED", order.getStatus());
    }

    @Test
    void toResponseCopiesTheOrderWithoutItems() {
        Customer customer = TestData.customer(12, "Testi", "Asiakas", "testi@example.com");
        Order order = new Order();
        order.setId(50);
        order.setCustomer(customer);
        order.setShippingAddress(TestData.customerAddress(30, customer, "Testikatu 1", "Helsinki"));
        order.setOrderDate(ORDER_DATE);
        order.setDeliveryDate(ORDER_DATE.plusDays(2));
        order.setStatus("SHIPPED");

        OrderResponse response = mapper.toResponse(order);

        assertEquals(50, response.getId());
        assertSame(customer, response.getCustomer());
        assertSame(order.getShippingAddress(), response.getShippingAddress());
        assertEquals(ORDER_DATE, response.getOrderDate());
        assertEquals(ORDER_DATE.plusDays(2), response.getDeliveryDate());
        assertEquals("SHIPPED", response.getStatus());
        assertNull(response.getOrderItems());
    }

    @Test
    void orderItemMapperUsesTheProductId() {
        OrderItem item = new OrderItem();
        item.setId(new OrderItemId(50, 8));
        item.setProduct(TestData.product(8, "Test Product", "49.90", 10, null, 5));
        item.setQuantity(3);
        item.setUnitPrice(new BigDecimal("45.00"));

        OrderItemResponse response = new OrderItemMapper().toResponse(item);

        assertEquals(new OrderItemId(50, 8), response.getId());
        assertEquals(8, response.getProductId());
        assertEquals(3, response.getQuantity());
        assertEquals(new BigDecimal("45.00"), response.getUnitPrice());
    }

    @Test
    void orderItemIdEqualityIsByBothKeys() {
        assertEquals(new OrderItemId(50, 8), new OrderItemId(50, 8));
        assertEquals(new OrderItemId(50, 8).hashCode(), new OrderItemId(50, 8).hashCode());
        assertTrue(!new OrderItemId(50, 8).equals(new OrderItemId(50, 9)));
        assertTrue(!new OrderItemId(50, 8).equals(new OrderItemId(51, 8)));
    }
}
