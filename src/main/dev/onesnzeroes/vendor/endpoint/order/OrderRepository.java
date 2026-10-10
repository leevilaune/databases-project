package dev.onesnzeroes.vendor.endpoint.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    @Query(value = """
            SELECT o FROM Order o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:customerId IS NULL OR o.customer.id = :customerId)
              AND (:supplierId IS NULL OR EXISTS (
                    SELECT 1 FROM OrderItem i
                    WHERE i.order = o AND i.product.supplierId = :supplierId))
            """,
            countQuery = """
            SELECT COUNT(o) FROM Order o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:customerId IS NULL OR o.customer.id = :customerId)
              AND (:supplierId IS NULL OR EXISTS (
                    SELECT 1 FROM OrderItem i
                    WHERE i.order = o AND i.product.supplierId = :supplierId))
            """)
    Page<Order> search(@Param("status") String status,
                       @Param("customerId") Integer customerId,
                       @Param("supplierId") Integer supplierId,
                       Pageable pageable);

    @Modifying
    @Query("""
        DELETE FROM OrderStatusLog l
        WHERE l.order.id = :orderId
    """)
    int deleteStatusLogsForOrder(@Param("orderId") Integer orderId);
}
