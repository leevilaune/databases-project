package dev.onesnzeroes.vendor.endpoint.order.dto;

import dev.onesnzeroes.vendor.endpoint.address.CustomerAddress;
import dev.onesnzeroes.vendor.endpoint.customer.Customer;
import dev.onesnzeroes.vendor.endpoint.orderitem.OrderItem;
import dev.onesnzeroes.vendor.endpoint.orderitem.dto.OrderItemResponse;

import java.time.LocalDateTime;
import java.util.List;

public class OrderResponse {

    private Integer id;
    private Customer customer;
    private LocalDateTime orderDate;
    private LocalDateTime deliveryDate;
    private CustomerAddress shippingAddress;
    private String status;
    private List<OrderItemResponse> orderItems;

    public OrderResponse() {}

    public OrderResponse(
            Integer id,
            Customer customer,
            LocalDateTime orderDate,
            LocalDateTime deliveryDate,
            CustomerAddress shippingAddressId,
            String status,
            List<OrderItemResponse> orderItems) {
        this.id = id;
        this.customer = customer;
        this.orderDate = orderDate;
        this.deliveryDate = deliveryDate;
        this.shippingAddress = shippingAddressId;
        this.status = status;
        this.orderItems = orderItems;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
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

    public CustomerAddress getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(CustomerAddress shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Customer getCustomer() {
        return customer;
    }

    public List<OrderItemResponse> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItemResponse> orderItems) {
        this.orderItems = orderItems;
    }
}