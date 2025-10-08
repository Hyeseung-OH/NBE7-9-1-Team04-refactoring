package com.backend.domain.payment.repository;

import com.backend.domain.order.entity.Orders;
import com.backend.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByOrders(Orders orders);

    Optional<Payment> findByPaymentIdAndOrders_User_UserId(Long paymentId, Long userId);
}
