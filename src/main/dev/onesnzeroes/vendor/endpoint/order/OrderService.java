package dev.onesnzeroes.vendor.endpoint.order;

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
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

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

    public PageResponse<OrderResponse> getOrders(String status, Integer customerId, Integer supplierId,
                                                 int page, int size) {
        PageRequest pageable = PageRequest.of(PageResponse.page(page), PageResponse.size(size),
                Sort.by(Sort.Order.desc("orderDate"), Sort.Order.desc("id")));
        String statusFilter = status == null || status.isBlank() ? null : status;
        Page<Order> orders = orderRepository.search(statusFilter, customerId, supplierId, pageable);
        return PageResponse.of(orders.map(orderMapper::toResponse));
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
        Customer customer = findCustomer(request.getCustomerId());
        CustomerAddress shippingAddress = null;
        if (request.getShippingAddressId() != null) {
            shippingAddress = findShippingAddress(request.getShippingAddressId());
            if (!shippingAddress.getCustomer().getId().equals(customer.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Shipping address " + shippingAddress.getId() + " does not belong to customer " + customer.getId());
            }
        }

        List<CreateOrderItemRequest> itemRequests = request.getOrderItems();
        Set<Integer> productIds = new HashSet<>();
        for (CreateOrderItemRequest item : itemRequests) {
            if (!productIds.add(item.getProductId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Product " + item.getProductId() + " appears more than once; combine the quantities");
            }
        }

        Map<Integer, Product> products = productRepository.findAllByIdForUpdate(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Order order = orderMapper.toEntity(request);
        checkDeliveryDate(order);
        order.setCustomer(customer);
        order.setShippingAddress(shippingAddress);

        for (CreateOrderItemRequest itemRequest : itemRequests) {
            Product product = products.get(itemRequest.getProductId());
            if (product == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Product " + itemRequest.getProductId() + " not found");
            }
            if (!"CANCELLED".equals(order.getStatus()) && product.getStockQuantity() < itemRequest.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Insufficient stock for product " + product.getId() + ": "
                                + product.getStockQuantity() + " available, " + itemRequest.getQuantity() + " requested");
            }

            OrderItem item = new OrderItem();
            item.setId(new OrderItemId(null, product.getId()));
            item.setProduct(product);
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(itemRequest.getUnitPrice() != null
                    ? BigDecimal.valueOf(itemRequest.getUnitPrice())
                    : product.getPrice());
            item.setOrder(order);
            order.getItems().add(item);
        }

        Order savedOrder = orderRepository.saveAndFlush(order);

        OrderResponse response = orderMapper.toResponse(savedOrder);
        response.setOrderItems(toItemResponses(savedOrder.getItems()));
        return response;
    }

    @Transactional
    public OrderResponse updateOrder(Integer id, UpdateOrderRequest request) {
        Order order = findOrder(id);

        orderMapper.updateEntity(order, request);
        checkDeliveryDate(order);

        if (request.getCustomerId() != null) {
            order.setCustomer(findCustomer(request.getCustomerId()));
        }
        if (request.getShippingAddressId() != null) {
            order.setShippingAddress(findShippingAddress(request.getShippingAddressId()));
        }

        Order savedOrder = orderRepository.saveAndFlush(order);
        return orderMapper.toResponse(savedOrder);
    }

    @Transactional
    public void deleteOrder(Integer id) {
        Order order = findOrder(id);
        orderRepository.deleteStatusLogsForOrder(id);
        orderRepository.delete(order);
    }

    private static void checkDeliveryDate(Order order) {
        if (order.getDeliveryDate() != null && order.getOrderDate() != null
                && order.getDeliveryDate().isBefore(order.getOrderDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "deliveryDate: must not be before orderDate");
        }
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
