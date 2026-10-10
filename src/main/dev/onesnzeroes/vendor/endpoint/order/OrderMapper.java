package dev.onesnzeroes.vendor.endpoint.order;

import dev.onesnzeroes.vendor.endpoint.order.dto.CreateOrderRequest;
import dev.onesnzeroes.vendor.endpoint.order.dto.OrderResponse;
import dev.onesnzeroes.vendor.endpoint.order.dto.UpdateOrderRequest;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();

        response.setId(order.getId());
        response.setCustomer(order.getCustomer());
        response.setOrderDate(order.getOrderDate());
        response.setDeliveryDate(order.getDeliveryDate());
        response.setShippingAddress(order.getShippingAddress());
        response.setStatus(order.getStatus());

        return response;
    }

    public Order toEntity(CreateOrderRequest request) {
        Order order = new Order();
        order.setOrderDate(request.getOrderDate());
        order.setDeliveryDate(request.getDeliveryDate());
        order.setStatus(request.getStatus() != null ? request.getStatus() : "NEW");
        return order;
    }

    public void updateEntity(Order order, UpdateOrderRequest request) {
        if (request.getOrderDate() != null) {
            order.setOrderDate(request.getOrderDate());
        }
        if (request.getDeliveryDate() != null) {
            order.setDeliveryDate(request.getDeliveryDate());
        }
        if (request.getStatus() != null) {
            order.setStatus(request.getStatus());
        }
    }
}
