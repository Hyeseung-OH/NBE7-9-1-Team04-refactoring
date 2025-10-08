package com.backend.domain.payment.dto.response;

import com.backend.domain.payment.entity.Payment;
import com.backend.domain.payment.entity.PaymentMethod;
import com.backend.domain.payment.entity.PaymentStatus;
import lombok.Builder;

import java.time.LocalDateTime;

/*
 * 결제 요청 응답 DTO
 */
@Builder
public record PaymentCreateResponse(
        Long paymentId,
        Long orderId,
        int paymentAmount,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        LocalDateTime createDate,
        LocalDateTime modifyDate
) {
    public PaymentCreateResponse(Payment payment) {
        this(
                payment.getPaymentId(),
                payment.getOrders().getOrderId(),
                payment.getPaymentAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentStatus(),
                payment.getCreateDate(),
                payment.getModifyDate()
        );
    }
}
