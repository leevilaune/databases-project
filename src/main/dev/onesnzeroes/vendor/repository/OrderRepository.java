package dev.onesnzeroes.vendor.repository;

import dev.onesnzeroes.vendor.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {

}