package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.PaymentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByOrderCode(String orderCode);
    boolean existsByOrderCode(String orderCode);
    List<PaymentOrder> findTop8ByUserIdOrderByCreatedAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.orderCode = :orderCode")
    Optional<PaymentOrder> lockByOrderCode(@Param("orderCode") String orderCode);

    @Modifying
    @Query("""
            update PaymentOrder o
            set o.status = :expired
            where o.status = :pending
              and o.expiresAt < :now
            """)
    int expirePendingOrdersBefore(
            @Param("now") LocalDateTime now,
            @Param("pending") PaymentOrder.Status pending,
            @Param("expired") PaymentOrder.Status expired);
}
