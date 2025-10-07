package com.backend.domain.payment.entity;

import com.backend.domain.order.entity.Orders;
import com.backend.global.exception.BusinessException;
import com.backend.global.jpa.entity.BaseEntity;
import com.backend.global.response.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Payment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 결제 테이블 키
    private Long paymentId;

    // 결제 금액, NOT NULL
    @Column(nullable = false)
    private int paymentAmount;

    // 결제 유형, NOT NULL [ex: 카드 결제]
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    // 결제 상태, NOT NULL [결제 전 / 결제 완료]
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    // 주문 엔티티와 연결 관계, OneToOne
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Orders orders;

    @Builder
    public Payment(int paymentAmount, PaymentMethod  paymentMethod, Orders orders) {
        this.paymentAmount = paymentAmount;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = PaymentStatus.PENDING;
        this.orders = orders;
    }

    public void complete() {
        if (this.paymentStatus == PaymentStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_COMPLETED);
        }
        this.paymentStatus = PaymentStatus.COMPLETED;
    }

    public void fail() {
        this.paymentStatus = PaymentStatus.FAILED;
    }

    public void cancel() {
        if(this.paymentStatus == PaymentStatus.CANCELED) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CANCELLED);
        }
        this.paymentStatus = PaymentStatus.CANCELED;
    }
}
