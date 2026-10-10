package dev.onesnzeroes.vendor.endpoint.orderitem;

import dev.onesnzeroes.vendor.endpoint.orderitem.dto.OrderItemResponse;
import org.springframework.stereotype.Component;

@Component
public class OrderItemMapper {

    public OrderItemResponse toResponse(OrderItem orderItem){
        OrderItemResponse response = new OrderItemResponse(orderItem.getId(),
                orderItem.getProduct().getId(),
                orderItem.getQuantity(),
                orderItem.getUnitPrice());
        return response;
    }
}
