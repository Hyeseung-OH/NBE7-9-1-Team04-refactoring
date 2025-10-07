package com.backend.domain.order.entity;

import com.backend.domain.payment.entity.Payment;
import com.backend.domain.user.address.entity.Address;
import com.backend.domain.user.user.entity.Users;
import com.backend.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 테이블 (Order)
 * 용도: 고객의 주문 한 건에 대한 전체적인 요약 정보(영수증 머리글)를 저장합니다.
 */
@Entity
@Getter
@Table(name = "orders")
@NoArgsConstructor
public class Orders extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus orderStatus;

    @Column(nullable = false)
    private int orderAmount;

    // OrderDetails 엔티티와의 관계 (일대다)
    @OneToMany(mappedBy = "order", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderDetails> orderDetails = new ArrayList<>();

    // User 엔티티와의 관계 (다대일)
    @ManyToOne(fetch = FetchType.LAZY)
    private Users user;

    // Address 엔티티와의 관계 (다대일) - 주소 도메인이 만들어지면 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private Address address;

    // Payment 엔티티와의 관계 (일대일) - 결제 도메인이 만들어지면 연결
    @OneToOne(mappedBy = "orders", fetch = FetchType.LAZY)
    private Payment payment;

    public void addOrderDetails(List<OrderDetails> orderDetails) {
        for (OrderDetails detail : orderDetails) {
            this.orderDetails.add(detail);
            detail.setOrder(this);
        }
    }

    public void removePayment() {
        this.payment = null;
    }

    public void updatePayment(Payment payment) {
        this.payment = payment;
    }

    public void updateOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    public Orders(Users user, int orderAmount, OrderStatus orderStatus, Address address) {
        this.user = user;
        this.orderAmount = orderAmount;
        this.orderStatus = orderStatus;
        this.address = address;
    }
}