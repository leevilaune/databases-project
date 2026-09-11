package dev.onesnzeroes.vendor.controller;

import dev.onesnzeroes.vendor.model.Order;
import dev.onesnzeroes.vendor.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController

@RequestMapping("/api/orders")

public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<Order> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Integer id) {
        return orderService.getOrderById(id);
    }

    @PostMapping
    public Order createOrder(@RequestBody Order order) {
        return orderService.createOrder(order);
    }

    @DeleteMapping("/{id}")
    public void deleteOrder(@PathVariable Integer id) {
        orderService.deleteOrder(id);
    }
}