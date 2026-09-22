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
        order.setStatus(request.getStatus());
        return order;
    }
    /*
    TODO: update to use entities, im too lazy rn
    public void updateEntity(Order order, UpdateOrderRequest request) {
        order.setCustomerId(request.getCustomerId());
        order.setOrderDate(request.getOrderDate());
        order.setDeliveryDate(request.getDeliveryDate());
        order.setShippingAddressId(request.getShippingAddressId());
        order.setStatus(request.getStatus());
    }
     */
}