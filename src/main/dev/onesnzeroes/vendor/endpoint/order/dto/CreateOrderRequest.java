package dev.onesnzeroes.vendor.endpoint.order.dto;

import dev.onesnzeroes.vendor.endpoint.orderitem.dto.CreateOrderItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.List;

public class CreateOrderRequest {

    @NotNull
    private Integer customerId;
    @NotNull
    private LocalDateTime orderDate;
    private LocalDateTime deliveryDate;
    @NotNull
    private Integer shippingAddressId;
    @Pattern(regexp = "NEW|SHIPPED|CANCELLED", message = "must be NEW, SHIPPED or CANCELLED")
    private String status;
    @NotEmpty
    private List<@Valid CreateOrderItemRequest> orderItems;

    public CreateOrderRequest() {
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public LocalDateTime getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDateTime deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public Integer getShippingAddressId() {
        return shippingAddressId;
    }

    public void setShippingAddressId(Integer shippingAddressId) {
        this.shippingAddressId = shippingAddressId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<CreateOrderItemRequest> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<CreateOrderItemRequest> orderItems) {
        this.orderItems = orderItems;
    }
}