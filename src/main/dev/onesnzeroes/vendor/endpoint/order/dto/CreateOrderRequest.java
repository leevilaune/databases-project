package dev.onesnzeroes.vendor.endpoint.order.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class CreateOrderRequest {

    @NotNull
    private Integer customerId;
    @NotNull
    private LocalDateTime orderDate;
    private LocalDateTime deliveryDate;
    @NotNull
    private Integer shippingAddressId;
    @NotNull
    private String status;

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
}