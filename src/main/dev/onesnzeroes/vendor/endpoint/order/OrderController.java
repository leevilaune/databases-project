package dev.onesnzeroes.vendor.endpoint.order;

import dev.onesnzeroes.vendor.endpoint.common.DeleteResponse;
import dev.onesnzeroes.vendor.endpoint.order.dto.CreateOrderRequest;
import dev.onesnzeroes.vendor.endpoint.order.dto.OrderResponse;
import dev.onesnzeroes.vendor.endpoint.order.dto.UpdateOrderRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/cancel-old")
    public ResponseEntity<Integer> cancelOldOrders(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cutoff) {
        return ResponseEntity.ok(this.orderService.cancelOldOrders(cutoff));
    }

    @DeleteMapping("/delete-logs")
    public ResponseEntity<Integer> deleteLogs(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cutoff) {
        return ResponseEntity.ok(this.orderService.deleteOldLogs(cutoff));
    }

    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Integer id) {
        return orderService.getOrderById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest order) {
        return orderService.createOrder(order);
    }

    @PutMapping("/{id}")
    public OrderResponse updateOrder(@PathVariable Integer id, @RequestBody UpdateOrderRequest request) {
        return orderService.updateOrder(id, request);
    }

    @DeleteMapping("/{id}")
    public DeleteResponse deleteOrder(@PathVariable Integer id) {
        orderService.deleteOrder(id);
        return new DeleteResponse("Order deleted successfully", id);
    }
}