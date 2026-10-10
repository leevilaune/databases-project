package dev.onesnzeroes.vendor.endpoint.order;

import dev.onesnzeroes.vendor.TestData;
import dev.onesnzeroes.vendor.endpoint.address.CustomerAddress;
import dev.onesnzeroes.vendor.endpoint.address.CustomerAddressRepository;
import dev.onesnzeroes.vendor.endpoint.common.PageResponse;
import dev.onesnzeroes.vendor.endpoint.customer.Customer;
import dev.onesnzeroes.vendor.endpoint.customer.CustomerRepository;
import dev.onesnzeroes.vendor.endpoint.order.dto.CreateOrderRequest;
import dev.onesnzeroes.vendor.endpoint.order.dto.OrderResponse;
import dev.onesnzeroes.vendor.endpoint.order.dto.UpdateOrderRequest;
import dev.onesnzeroes.vendor.endpoint.orderitem.OrderItem;
import dev.onesnzeroes.vendor.endpoint.orderitem.OrderItemId;
import dev.onesnzeroes.vendor.endpoint.orderitem.OrderItemMapper;
import dev.onesnzeroes.vendor.endpoint.orderitem.OrderItemRepository;
import dev.onesnzeroes.vendor.endpoint.orderitem.dto.CreateOrderItemRequest;
import dev.onesnzeroes.vendor.endpoint.orderitem.dto.OrderItemResponse;
import dev.onesnzeroes.vendor.endpoint.product.Product;
import dev.onesnzeroes.vendor.endpoint.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final LocalDateTime ORDER_DATE = LocalDateTime.of(2026, 10, 1, 10, 0);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerAddressRepository addressRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    private OrderService service;

    private Customer customer;
    private CustomerAddress address;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, customerRepository, addressRepository, new OrderMapper(),
                orderItemRepository, new OrderItemMapper(), productRepository);
        customer = TestData.customer(12, "Testi", "Asiakas", "testi@example.com");
        address = TestData.customerAddress(30, customer, "Testikatu 1", "Helsinki");
        product = TestData.product(8, "Test Product", "49.90", 10, null, 5);
    }

    private static CreateOrderRequest createRequest(CreateOrderItemRequest... items) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(12);
        request.setOrderDate(ORDER_DATE);
        request.setShippingAddressId(30);
        request.setOrderItems(new ArrayList<>(List.of(items)));
        return request;
    }

    private void givenCustomerAndAddress() {
        when(customerRepository.findById(12)).thenReturn(Optional.of(customer));
        when(addressRepository.findById(30)).thenReturn(Optional.of(address));
    }

    private void givenProducts(Product... products) {
        when(productRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(products));
    }

    private void givenSaveAssignsId(int id) {
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(id);
            return order;
        });
    }

    private Order existingOrder(int id, String status) {
        Order order = new Order();
        order.setId(id);
        order.setCustomer(customer);
        order.setShippingAddress(address);
        order.setOrderDate(ORDER_DATE);
        order.setStatus(status);
        return order;
    }

    @Test
    void getOrdersPassesFiltersAndSortsNewestFirst() {
        Order order = existingOrder(50, "NEW");
        when(orderRepository.search(eq("NEW"), eq(12), eq(5), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        PageResponse<OrderResponse> result = service.getOrders("NEW", 12, 5, 0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).search(eq("NEW"), eq(12), eq(5), captor.capture());
        assertEquals(Sort.by(Sort.Order.desc("orderDate"), Sort.Order.desc("id")), captor.getValue().getSort());
        assertEquals(1, result.getTotalElements());
        assertEquals(50, result.getContent().get(0).getId());
        assertSame(customer, result.getContent().get(0).getCustomer());
    }

    @Test
    void getOrdersTreatsABlankStatusAsNoFilterAndClampsPaging() {
        when(orderRepository.search(isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(Page.empty());

        service.getOrders("  ", null, null, -1, 1000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).search(isNull(), isNull(), isNull(), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void getOrderByIdIncludesTheItems() {
        Order order = existingOrder(50, "NEW");
        OrderItem item = new OrderItem();
        item.setId(new OrderItemId(50, 8));
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("49.90"));
        when(orderRepository.findById(50)).thenReturn(Optional.of(order));
        when(orderItemRepository.findById_OrderId(50)).thenReturn(List.of(item));

        OrderResponse response = service.getOrderById(50);

        assertEquals(50, response.getId());
        assertEquals("NEW", response.getStatus());
        assertEquals(1, response.getOrderItems().size());
        OrderItemResponse line = response.getOrderItems().get(0);
        assertEquals(8, line.getProductId());
        assertEquals(2, line.getQuantity());
        assertEquals(new BigDecimal("49.90"), line.getUnitPrice());
        assertEquals(new OrderItemId(50, 8), line.getId());
    }

    @Test
    void getUnknownOrderIs404() {
        when(orderRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Order not found", () -> service.getOrderById(999));
        verifyNoInteractions(orderItemRepository);
    }

    @Test
    void createOrderSavesTheOrderWithItsLines() {
        givenCustomerAndAddress();
        givenProducts(product);
        givenSaveAssignsId(50);

        OrderResponse response = service.createOrder(createRequest(new CreateOrderItemRequest(8, 2, null)));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        Order saved = captor.getValue();
        assertSame(customer, saved.getCustomer());
        assertSame(address, saved.getShippingAddress());
        assertEquals("NEW", saved.getStatus());
        assertEquals(ORDER_DATE, saved.getOrderDate());
        assertEquals(1, saved.getItems().size());
        OrderItem line = saved.getItems().get(0);
        assertSame(saved, line.getOrder());
        assertSame(product, line.getProduct());
        assertEquals(2, line.getQuantity());

        assertEquals(50, response.getId());
        assertEquals("NEW", response.getStatus());
        assertEquals(1, response.getOrderItems().size());
        assertEquals(8, response.getOrderItems().get(0).getProductId());
    }

    @Test
    void createOrderUsesTheProductPriceWhenNoUnitPriceIsGiven() {
        givenCustomerAndAddress();
        givenProducts(product);
        givenSaveAssignsId(50);

        OrderResponse response = service.createOrder(createRequest(new CreateOrderItemRequest(8, 1, null)));

        assertEquals(new BigDecimal("49.90"), response.getOrderItems().get(0).getUnitPrice());
    }

    @Test
    void createOrderUsesTheGivenUnitPrice() {
        givenCustomerAndAddress();
        givenProducts(product);
        givenSaveAssignsId(50);

        OrderResponse response = service.createOrder(createRequest(new CreateOrderItemRequest(8, 1, 39.5)));

        assertEquals(0, new BigDecimal("39.5").compareTo(response.getOrderItems().get(0).getUnitPrice()));
    }

    @Test
    void createOrderKeepsTheGivenStatus() {
        givenCustomerAndAddress();
        givenProducts(product);
        givenSaveAssignsId(50);
        CreateOrderRequest request = createRequest(new CreateOrderItemRequest(8, 1, null));
        request.setStatus("SHIPPED");

        assertEquals("SHIPPED", service.createOrder(request).getStatus());
    }

    @Test
    void createOrderLocksAllOrderedProductsInOneQuery() {
        Product other = TestData.product(9, "Other", "5.00", 3, null, 5);
        givenCustomerAndAddress();
        givenProducts(product, other);
        givenSaveAssignsId(50);

        service.createOrder(createRequest(new CreateOrderItemRequest(8, 1, null), new CreateOrderItemRequest(9, 3, null)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Integer>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(productRepository).findAllByIdForUpdate(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertTrue(captor.getValue().containsAll(List.of(8, 9)));
    }

    @Test
    void createOrderWithMoreThanTheStockIs409AndSavesNothing() {
        givenCustomerAndAddress();
        givenProducts(product);

        TestData.assertStatus(HttpStatus.CONFLICT, "Insufficient stock for product 8: 10 available, 1000 requested",
                () -> service.createOrder(createRequest(new CreateOrderItemRequest(8, 1000, null))));
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createCancelledOrderSkipsTheStockCheck() {
        givenCustomerAndAddress();
        givenProducts(product);
        givenSaveAssignsId(50);
        CreateOrderRequest request = createRequest(new CreateOrderItemRequest(8, 1000, null));
        request.setStatus("CANCELLED");

        assertEquals("CANCELLED", service.createOrder(request).getStatus());
    }

    @Test
    void createOrderWithTheSameProductTwiceIs400() {
        givenCustomerAndAddress();

        TestData.assertStatus(HttpStatus.BAD_REQUEST, "Product 8 appears more than once; combine the quantities",
                () -> service.createOrder(createRequest(
                        new CreateOrderItemRequest(8, 1, null), new CreateOrderItemRequest(8, 2, null))));
        verifyNoInteractions(productRepository);
    }

    @Test
    void createOrderWithAnotherCustomersAddressIs400() {
        Customer other = TestData.customer(1, "Muu", "Asiakas", "muu@example.com");
        when(customerRepository.findById(12)).thenReturn(Optional.of(customer));
        when(addressRepository.findById(30)).thenReturn(Optional.of(TestData.customerAddress(30, other, "Muukatu 1", "Turku")));

        TestData.assertStatus(HttpStatus.BAD_REQUEST, "Shipping address 30 does not belong to customer 12",
                () -> service.createOrder(createRequest(new CreateOrderItemRequest(8, 1, null))));
        verifyNoInteractions(productRepository);
    }

    @Test
    void createOrderWithUnknownCustomerIs400() {
        when(customerRepository.findById(12)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.BAD_REQUEST, "Customer not found",
                () -> service.createOrder(createRequest(new CreateOrderItemRequest(8, 1, null))));
        verifyNoInteractions(addressRepository, productRepository);
    }

    @Test
    void createOrderWithUnknownAddressIs400() {
        when(customerRepository.findById(12)).thenReturn(Optional.of(customer));
        when(addressRepository.findById(30)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.BAD_REQUEST, "Shipping address not found",
                () -> service.createOrder(createRequest(new CreateOrderItemRequest(8, 1, null))));
    }

    @Test
    void createOrderWithUnknownProductIs400() {
        givenCustomerAndAddress();
        givenProducts();

        TestData.assertStatus(HttpStatus.BAD_REQUEST, "Product 999 not found",
                () -> service.createOrder(createRequest(new CreateOrderItemRequest(999, 1, null))));
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createOrderWithDeliveryBeforeOrderDateIs400() {
        givenCustomerAndAddress();
        givenProducts(product);
        CreateOrderRequest request = createRequest(new CreateOrderItemRequest(8, 1, null));
        request.setDeliveryDate(ORDER_DATE.minusDays(1));

        TestData.assertStatus(HttpStatus.BAD_REQUEST, "deliveryDate: must not be before orderDate",
                () -> service.createOrder(request));
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createOrderAllowsDeliveryOnTheOrderDate() {
        givenCustomerAndAddress();
        givenProducts(product);
        givenSaveAssignsId(50);
        CreateOrderRequest request = createRequest(new CreateOrderItemRequest(8, 1, null));
        request.setDeliveryDate(ORDER_DATE);

        assertEquals(ORDER_DATE, service.createOrder(request).getDeliveryDate());
    }

    @Test
    void updateOrderChangesOnlyTheGivenFields() {
        Order order = existingOrder(50, "NEW");
        when(orderRepository.findById(50)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus("SHIPPED");
        request.setDeliveryDate(ORDER_DATE.plusDays(2));

        OrderResponse response = service.updateOrder(50, request);

        assertEquals("SHIPPED", response.getStatus());
        assertEquals(ORDER_DATE.plusDays(2), response.getDeliveryDate());
        assertEquals(ORDER_DATE, response.getOrderDate());
        assertSame(customer, response.getCustomer());
        assertSame(address, response.getShippingAddress());
        verifyNoInteractions(customerRepository, addressRepository);
    }

    @Test
    void updateOrderCanMoveItToAnotherCustomerAndAddress() {
        Customer other = TestData.customer(13, "Uusi", "Asiakas", "uusi@example.com");
        CustomerAddress otherAddress = TestData.customerAddress(31, other, "Uusikatu 1", "Oulu");
        Order order = existingOrder(50, "NEW");
        when(orderRepository.findById(50)).thenReturn(Optional.of(order));
        when(customerRepository.findById(13)).thenReturn(Optional.of(other));
        when(addressRepository.findById(31)).thenReturn(Optional.of(otherAddress));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setCustomerId(13);
        request.setShippingAddressId(31);

        OrderResponse response = service.updateOrder(50, request);

        assertSame(other, response.getCustomer());
        assertSame(otherAddress, response.getShippingAddress());
        assertEquals("NEW", response.getStatus());
    }

    @Test
    void updateOrderWithDeliveryBeforeOrderDateIs400() {
        when(orderRepository.findById(50)).thenReturn(Optional.of(existingOrder(50, "NEW")));
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setDeliveryDate(LocalDateTime.of(2000, 1, 1, 0, 0));

        TestData.assertStatus(HttpStatus.BAD_REQUEST, "deliveryDate: must not be before orderDate",
                () -> service.updateOrder(50, request));
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateOrderWithUnknownCustomerIs400() {
        when(orderRepository.findById(50)).thenReturn(Optional.of(existingOrder(50, "NEW")));
        when(customerRepository.findById(999)).thenReturn(Optional.empty());
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setCustomerId(999);

        TestData.assertStatus(HttpStatus.BAD_REQUEST, "Customer not found", () -> service.updateOrder(50, request));
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateUnknownOrderIs404() {
        when(orderRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Order not found",
                () -> service.updateOrder(999, new UpdateOrderRequest()));
    }

    @Test
    void deleteOrderRemovesTheStatusLogFirst() {
        Order order = existingOrder(51, "CANCELLED");
        when(orderRepository.findById(51)).thenReturn(Optional.of(order));

        service.deleteOrder(51);

        InOrder inOrder = inOrder(orderRepository);
        inOrder.verify(orderRepository).deleteStatusLogsForOrder(51);
        inOrder.verify(orderRepository).delete(order);
    }

    @Test
    void deleteUnknownOrderIs404AndDeletesNothing() {
        when(orderRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Order not found", () -> service.deleteOrder(999));
        verify(orderRepository, never()).deleteStatusLogsForOrder(anyInt());
        verify(orderRepository, never()).delete(any());
    }
}
