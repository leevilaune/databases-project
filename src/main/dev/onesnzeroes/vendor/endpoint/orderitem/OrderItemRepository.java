package dev.onesnzeroes.vendor.endpoint.orderitem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, OrderItemId> {

    List<OrderItem> findById_OrderId(Integer orderId);}