package dev.onesnzeroes.vendor.endpoint.order.dto;

import java.time.LocalDateTime;

public class OrderResponse {

    private Integer id;
    private Integer customerId;
    private LocalDateTime orderDate;
    private LocalDateTime deliveryDate;
    private Integer shippingAddressId;
    private String status;

    public OrderResponse() {
    }

    public OrderResponse(
            Integer id,
            Integer customerId,
            LocalDateTime orderDate,
            LocalDateTime deliveryDate,
            Integer shippingAddressId,
            String status
    ) {
        this.id = id;
        this.customerId = customerId;
        this.orderDate = orderDate;
        this.deliveryDate = deliveryDate;
        this.shippingAddressId = shippingAddressId;
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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