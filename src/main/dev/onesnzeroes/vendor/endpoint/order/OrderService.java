package dev.onesnzeroes.vendor.endpoint.order;

import dev.onesnzeroes.vendor.endpoint.address.CustomerAddress;
import dev.onesnzeroes.vendor.endpoint.address.CustomerAddressRepository;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    @PersistenceContext
    private EntityManager entityManager;

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final OrderMapper orderMapper;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemMapper orderItemMapper;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository,
                        CustomerRepository customerRepository,
                        CustomerAddressRepository customerAddressRepository,
                        OrderMapper orderMapper,
                        OrderItemRepository orderItemRepository,
                        OrderItemMapper orderItemMapper,
                        ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.customerAddressRepository = customerAddressRepository;
        this.orderMapper = orderMapper;
        this.orderItemRepository = orderItemRepository;
        this.orderItemMapper = orderItemMapper;
        this.productRepository = productRepository;
    }

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    public OrderResponse getOrderById(Integer id) {
        Order order = findOrder(id);

        OrderResponse response = orderMapper.toResponse(order);

        List<OrderItem> items = orderItemRepository.findById_OrderId(id);
        response.setOrderItems(toItemResponses(items));

        return response;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = orderMapper.toEntity(request);
        Customer customer = findCustomer(request.getCustomerId());

        CustomerAddress shippingAddress = null;

        if (request.getShippingAddressId() != null) {
            shippingAddress = findShippingAddress(request.getShippingAddressId());
        }

        List<CreateOrderItemRequest> itemRequests =
                request.getOrderItems() != null ? request.getOrderItems() : List.of();

        for (CreateOrderItemRequest itemRequest : itemRequests) {

            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product not found"));

            OrderItem item = new OrderItem();
            item.setId(new OrderItemId(order.getId(), product.getId()));
            item.setProduct(product);
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(BigDecimal.valueOf(itemRequest.getUnitPrice()));

            item.setOrder(order);

            order.getItems().add(item);
        }
        order.setCustomer(customer);
        order.setShippingAddress(shippingAddress);

        Order savedOrder = orderRepository.save(order);

        OrderResponse response = orderMapper.toResponse(savedOrder);
        response.setOrderItems(toItemResponses(savedOrder.getItems()));
        return response;
    }

    @Transactional
    public OrderResponse updateOrder(Integer id, UpdateOrderRequest request) {
        Order order = findOrder(id);

        orderMapper.updateEntity(order, request);

        if (request.getCustomerId() != null) {
            order.setCustomer(findCustomer(request.getCustomerId()));
        }
        if (request.getShippingAddressId() != null) {
            order.setShippingAddress(findShippingAddress(request.getShippingAddressId()));
        }

        Order savedOrder = orderRepository.save(order);
        return orderMapper.toResponse(savedOrder);
    }

    @Transactional
    public void deleteOrder(Integer id) {
        Order order = findOrder(id);
        orderRepository.deleteStatusLogsForOrder(id);
        orderRepository.delete(order);
    }

    @Transactional
    public int cancelOldOrders(LocalDateTime cutoff) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<Order> update = cb.createCriteriaUpdate(Order.class);
        Root<Order> order = update.from(Order.class);

        update.set(order.get("status"), "CANCELLED");
        update.where(cb.and(cb.equal(order.get("status"), "NEW"), cb.lessThan(order.get("orderDate"), cutoff)));

        return entityManager.createQuery(update).executeUpdate();
    }

    @Transactional
    public int deleteOldLogs(LocalDateTime cutoff) {
        return orderRepository.deleteOldStatusLogs(cutoff);
    }

    private Order findOrder(Integer id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private Customer findCustomer(Integer id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer not found"));
    }

    private CustomerAddress findShippingAddress(Integer id) {
        return customerAddressRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipping address not found"));
    }

    private List<OrderItemResponse> toItemResponses(List<OrderItem> items) {
        List<OrderItemResponse> responses = new ArrayList<>();
        for (OrderItem oi : items) {
            responses.add(this.orderItemMapper.toResponse(oi));
        }
        return responses;
    }
}