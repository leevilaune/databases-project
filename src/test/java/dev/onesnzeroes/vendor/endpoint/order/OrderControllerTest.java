package dev.onesnzeroes.vendor.endpoint.order;

import dev.onesnzeroes.vendor.TestData;
import dev.onesnzeroes.vendor.endpoint.common.PageResponse;
import dev.onesnzeroes.vendor.endpoint.customer.Customer;
import dev.onesnzeroes.vendor.endpoint.order.dto.CreateOrderRequest;
import dev.onesnzeroes.vendor.endpoint.order.dto.OrderResponse;
import dev.onesnzeroes.vendor.endpoint.order.dto.UpdateOrderRequest;
import dev.onesnzeroes.vendor.endpoint.orderitem.OrderItemId;
import dev.onesnzeroes.vendor.endpoint.orderitem.dto.OrderItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final String VALID_ORDER = """
            {"customerId":12,"orderDate":"2026-10-01T10:00:00","shippingAddressId":30,
             "orderItems":[{"productId":8,"quantity":2}]}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService orderService;

    private static OrderResponse response(int id, String status) {
        Customer customer = TestData.customer(12, "Testi", "Asiakas", "testi@example.com");
        OrderResponse response = new OrderResponse();
        response.setId(id);
        response.setCustomer(customer);
        response.setOrderDate(LocalDateTime.of(2026, 10, 1, 10, 0));
        response.setShippingAddress(TestData.customerAddress(30, customer, "Testikatu 1", "Helsinki"));
        response.setStatus(status);
        return response;
    }

    private static String order(String items) {
        return "{\"customerId\":12,\"orderDate\":\"2026-10-01T10:00:00\",\"shippingAddressId\":30,\"orderItems\":"
                + items + "}";
    }

    @Test
    @DisplayName("POST /orders creates the order with its items -> 201")
    void create() throws Exception {
        OrderResponse created = response(50, "NEW");
        created.setOrderItems(List.of(new OrderItemResponse(new OrderItemId(50, 8), 8, 2, new BigDecimal("49.90"))));
        when(orderService.createOrder(any())).thenReturn(created);

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.customer.id").value(12))
                .andExpect(jsonPath("$.shippingAddress.id").value(30))
                .andExpect(jsonPath("$.orderItems", hasSize(1)))
                .andExpect(jsonPath("$.orderItems[0].productId").value(8));

        ArgumentCaptor<CreateOrderRequest> captor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderService).createOrder(captor.capture());
        assertEquals(12, captor.getValue().getCustomerId());
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), captor.getValue().getOrderDate());
        assertNull(captor.getValue().getStatus());
        assertEquals(2, captor.getValue().getOrderItems().get(0).getQuantity());
        assertNull(captor.getValue().getOrderItems().get(0).getUnitPrice());
    }

    @Test
    @DisplayName("POST /orders with more than the stock -> 409")
    void createOverStock() throws Exception {
        when(orderService.createOrder(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "Insufficient stock for product 8: 10 available, 1000 requested"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order("[{\"productId\":8,\"quantity\":1000}]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Insufficient stock")));
    }

    @Test
    @DisplayName("POST /orders with the same product twice -> 400")
    void createDuplicateProduct() throws Exception {
        when(orderService.createOrder(any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Product 8 appears more than once; combine the quantities"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order("[{\"productId\":8,\"quantity\":1},{\"productId\":8,\"quantity\":1}]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("more than once")));
    }

    @Test
    @DisplayName("POST /orders with another customer's address -> 400")
    void createWithOtherCustomersAddress() throws Exception {
        when(orderService.createOrder(any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Shipping address 1 does not belong to customer 12"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("does not belong")));
    }

    @Test
    @DisplayName("POST /orders with unknown customer -> 400")
    void createWithUnknownCustomer() throws Exception {
        when(orderService.createOrder(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer not found"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Customer not found"));
    }

    @Test
    @DisplayName("POST /orders with unknown product -> 400")
    void createWithUnknownProduct() throws Exception {
        when(orderService.createOrder(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product 999999 not found"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order("[{\"productId\":999999,\"quantity\":1}]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("not found")));
    }

    @Test
    @DisplayName("POST /orders without items -> 400")
    void createWithoutItems() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(order("[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("orderItems")));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("POST /orders with an invalid status -> 400")
    void createWithInvalidStatus() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":12,\"orderDate\":\"2026-10-01T10:00:00\",\"shippingAddressId\":30,"
                                + "\"status\":\"PENDING\",\"orderItems\":[{\"productId\":8,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("must be NEW, SHIPPED or CANCELLED")));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("POST /orders with a zero quantity or negative unit price -> 400")
    void createWithInvalidItem() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order("[{\"productId\":8,\"quantity\":0,\"unitPrice\":-1}]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("orderItems[0].quantity")))
                .andExpect(jsonPath("$.detail").value(containsString("orderItems[0].unitPrice")));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("POST /orders without customer, date and address -> 400")
    void createWithoutRequiredFields() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderItems\":[{\"productId\":8,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("customerId")))
                .andExpect(jsonPath("$.detail").value(containsString("orderDate")))
                .andExpect(jsonPath("$.detail").value(containsString("shippingAddressId")));
    }

    @Test
    @DisplayName("GET /orders/{id} returns the order with items")
    void getOne() throws Exception {
        OrderResponse order = response(50, "NEW");
        order.setOrderItems(List.of(new OrderItemResponse(new OrderItemId(50, 8), 8, 2, new BigDecimal("49.90"))));
        when(orderService.getOrderById(50)).thenReturn(order);

        mvc.perform(get("/api/orders/50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.orderItems[0].quantity").value(2))
                .andExpect(jsonPath("$.orderItems[0].unitPrice").value(49.9));
    }

    @Test
    @DisplayName("GET /orders?customerId= lists the customer's orders")
    void listByCustomer() throws Exception {
        when(orderService.getOrders(isNull(), eq(12), isNull(), eq(0), eq(20)))
                .thenReturn(PageResponse.of(List.of(response(50, "NEW")), 0, 20, 1));

        mvc.perform(get("/api/orders").param("customerId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customer.id").value(12));
    }

    @Test
    @DisplayName("GET /orders?supplierId= lists orders containing the supplier's products")
    void listBySupplier() throws Exception {
        when(orderService.getOrders(isNull(), isNull(), eq(5), eq(0), eq(20)))
                .thenReturn(PageResponse.of(List.of(response(50, "NEW")), 0, 20, 1));

        mvc.perform(get("/api/orders").param("supplierId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(50));
    }

    @Test
    @DisplayName("GET /orders is paginated")
    void listPaginated() throws Exception {
        when(orderService.getOrders(isNull(), isNull(), isNull(), eq(1), eq(2)))
                .thenReturn(PageResponse.of(List.of(response(48, "NEW"), response(47, "SHIPPED")), 1, 2, 5));

        mvc.perform(get("/api/orders").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    @DisplayName("GET /orders?status=CANCELLED filters by status")
    void listByStatus() throws Exception {
        when(orderService.getOrders(eq("CANCELLED"), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(PageResponse.of(List.of(response(51, "CANCELLED")), 0, 20, 1));

        mvc.perform(get("/api/orders").param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PUT /orders/{id} ships the order (partial update)")
    void ship() throws Exception {
        OrderResponse shipped = response(50, "SHIPPED");
        shipped.setDeliveryDate(LocalDateTime.of(2026, 10, 3, 12, 0));
        when(orderService.updateOrder(eq(50), any())).thenReturn(shipped);

        mvc.perform(put("/api/orders/50").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\",\"deliveryDate\":\"2026-10-03T12:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.deliveryDate").value("2026-10-03T12:00:00"));

        ArgumentCaptor<UpdateOrderRequest> captor = ArgumentCaptor.forClass(UpdateOrderRequest.class);
        verify(orderService).updateOrder(eq(50), captor.capture());
        assertEquals("SHIPPED", captor.getValue().getStatus());
        assertNull(captor.getValue().getCustomerId());
        assertNull(captor.getValue().getOrderDate());
    }

    @Test
    @DisplayName("PUT SHIPPED -> NEW is rejected by the status trigger -> 409")
    void shippedBackToNew() throws Exception {
        when(orderService.updateOrder(eq(50), any())).thenThrow(TestData.triggerError(
                "Order status cannot change: only NEW -> SHIPPED and NEW -> CANCELLED are allowed"));

        mvc.perform(put("/api/orders/50").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"NEW\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Order status cannot change: only NEW -> SHIPPED and NEW -> CANCELLED are allowed"));
    }

    @Test
    @DisplayName("PUT a delivery date before the order date -> 400")
    void deliveryBeforeOrder() throws Exception {
        when(orderService.updateOrder(eq(50), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "deliveryDate: must not be before orderDate"));

        mvc.perform(put("/api/orders/50").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryDate\":\"2000-01-01T00:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("deliveryDate")));
    }

    @Test
    @DisplayName("PUT an invalid status -> 400")
    void updateWithInvalidStatus() throws Exception {
        mvc.perform(put("/api/orders/50").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("status")));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("PUT /orders/{id} with status CANCELLED cancels it")
    void cancel() throws Exception {
        when(orderService.updateOrder(eq(51), any())).thenReturn(response(51, "CANCELLED"));

        mvc.perform(put("/api/orders/51").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("A database error that is not a business rule -> 500")
    void otherDatabaseError() throws Exception {
        when(orderService.updateOrder(eq(50), any()))
                .thenThrow(new DataAccessResourceFailureException("connection lost"));

        mvc.perform(put("/api/orders/50").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Database error"));
    }

    @Test
    @DisplayName("GET /orders/{unknown id} -> 404")
    void getUnknown() throws Exception {
        when(orderService.getOrderById(999999))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        mvc.perform(get("/api/orders/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Order not found"));
    }

    @Test
    @DisplayName("PUT /orders/{unknown id} -> 404")
    void updateUnknown() throws Exception {
        when(orderService.updateOrder(eq(999999), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        mvc.perform(put("/api/orders/999999").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /orders/{id} deletes the order")
    void deleteOrder() throws Exception {
        mvc.perform(delete("/api/orders/51"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Order deleted successfully"))
                .andExpect(jsonPath("$.id").value(51));
        verify(orderService).deleteOrder(51);
    }

    @Test
    @DisplayName("DELETE /orders/{unknown id} -> 404")
    void deleteUnknown() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"))
                .when(orderService).deleteOrder(999999);

        mvc.perform(delete("/api/orders/999999"))
                .andExpect(status().isNotFound());
    }
}
