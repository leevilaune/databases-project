package dev.onesnzeroes.vendor.endpoint.order;

import dev.onesnzeroes.vendor.endpoint.address.CustomerAddress;
import dev.onesnzeroes.vendor.endpoint.address.CustomerAddressRepository;
import dev.onesnzeroes.vendor.endpoint.customer.Customer;
import dev.onesnzeroes.vendor.endpoint.customer.CustomerRepository;
import dev.onesnzeroes.vendor.endpoint.order.dto.CreateOrderRequest;
import dev.onesnzeroes.vendor.endpoint.order.dto.OrderResponse;
import dev.onesnzeroes.vendor.endpoint.orderitem.OrderItem;
import dev.onesnzeroes.vendor.endpoint.orderitem.OrderItemId;
import dev.onesnzeroes.vendor.endpoint.orderitem.OrderItemMapper;
import dev.onesnzeroes.vendor.endpoint.orderitem.OrderItemRepository;
import dev.onesnzeroes.vendor.endpoint.orderitem.dto.CreateOrderItemRequest;
import dev.onesnzeroes.vendor.endpoint.orderitem.dto.OrderItemResponse;
import dev.onesnzeroes.vendor.endpoint.product.Product;
import dev.onesnzeroes.vendor.endpoint.product.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    public OrderResponse getOrderById(Integer id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        OrderResponse response = orderMapper.toResponse(order);

        List<OrderItem> items = orderItemRepository.findById_OrderId(id);
        List<OrderItemResponse> orderItemResponses = new ArrayList<>();
        for(OrderItem oi : items){
            orderItemResponses.add(this.orderItemMapper.toResponse(oi));
        }
        response.setOrderItems(orderItemResponses);

        return response;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = orderMapper.toEntity(request);
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        CustomerAddress shippingAddress = null;

        if (request.getShippingAddressId() != null) {
            shippingAddress = customerAddressRepository
                    .findById(request.getShippingAddressId())
                    .orElseThrow(() -> new RuntimeException("Shipping address not found"));
        }

        for (CreateOrderItemRequest itemRequest : request.getOrderItems()) {

            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

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

        return orderMapper.toResponse(savedOrder);
    }

    public void deleteOrder(Integer id) {
        orderRepository.deleteById(id);
    }
}