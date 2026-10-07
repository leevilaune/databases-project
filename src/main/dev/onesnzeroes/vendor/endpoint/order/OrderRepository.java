package dev.onesnzeroes.vendor.endpoint.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    @Modifying
    @Query("""
        DELETE FROM OrderStatusLog l
        WHERE l.changedAt < :cutoff
    """)
    int deleteOldStatusLogs(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("""
        DELETE FROM OrderStatusLog l
        WHERE l.order.id = :orderId
    """)
    int deleteStatusLogsForOrder(@Param("orderId") Integer orderId);
}