package com.backend.domain.payment.service;

import com.backend.domain.order.entity.Orders;
import com.backend.domain.order.repository.OrderRepository;
import com.backend.domain.payment.dto.request.PaymentCreateRequest;
import com.backend.domain.payment.dto.response.PaymentCancelResponse;
import com.backend.domain.payment.dto.response.PaymentCreateResponse;
import com.backend.domain.payment.dto.response.PaymentInquiryResponse;
import com.backend.domain.payment.entity.Payment;
import com.backend.domain.payment.entity.PaymentMethod;
import com.backend.domain.payment.entity.PaymentStatus;
import com.backend.domain.payment.repository.PaymentRepository;
import com.backend.domain.user.user.dto.UserDto;
import com.backend.global.exception.BusinessException;
import com.backend.global.response.ErrorCode;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class PaymentService {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRetry paymentRetry;

    // 결제 요청
    @Transactional
    public PaymentCreateResponse createPayment(PaymentCreateRequest request, UserDto currentUser) {
        try {
            Orders orders = orderRepository.findByOrderIdAndUser_UserId(request.orderId(), currentUser.userId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ORDER));

            if (orders.getOrderAmount() != request.paymentAmount()) {
                throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
            }

            if (request.paymentMethod() == null || request.paymentMethod() != PaymentMethod.CARD) {
                throw new BusinessException(ErrorCode.INVALID_PAYMENT_METHOD);
            }

            if (paymentRepository.existsByOrders(orders)) {
                throw new BusinessException(ErrorCode.PAYMENT_ALREADY_COMPLETED);
            }

            return paymentRetry.processPaymentWithRetry(request, orders);
        } catch (OptimisticLockException e) {
            throw new BusinessException(ErrorCode.CONCURRENT_PAYMENT_DETECTED);
        }
    }

    // 결제 단건 조회
    public PaymentInquiryResponse getPayment(Long paymentId, UserDto currentUser) {
        if (paymentId == null || paymentId <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND_PAYMENT);
        }

        Payment payment;
        if (currentUser.level() == 0) { // 관리자는 전체 조회 허용
            payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_PAYMENT));
        } else {
            payment = paymentRepository.findByPaymentIdAndOrders_User_UserId(paymentId, currentUser.userId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_PAYMENT));
        }

        return new PaymentInquiryResponse(payment);
    }

    // 결제 단건 취소 - controller
    @Transactional
    public PaymentCancelResponse cancelPayment(Long paymentId, UserDto currentUser) {
        if (paymentId == null || paymentId <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND_PAYMENT);
        }

        Payment payment = paymentRepository.findByPaymentIdAndOrders_User_UserId(paymentId, currentUser.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_PAYMENT));

        if (payment.getPaymentStatus() == PaymentStatus.CANCELED) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CANCELLED);
        }

        if (payment.getPaymentStatus() != PaymentStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_CANCELLABLE);
        }

        payment.cancel();

        return new PaymentCancelResponse(payment);
    }

    // 결제 단건 취소 - orderService 연동
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelPaymentByOrder(Orders order, UserDto user) {
        Payment payment = order.getPayment();

        if (payment.getPaymentStatus() != PaymentStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_CANCELLABLE);
        }

        payment.cancel();
    }

    // 취소된 결제 내역 삭제 - controller
    @Transactional
    public void deletePayment(Long paymentId, UserDto currentUser) {
        if (paymentId == null || paymentId <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND_PAYMENT);
        }

        Payment payment = paymentRepository.findByPaymentIdAndOrders_User_UserId(paymentId, currentUser.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_PAYMENT));

        if (payment.getPaymentStatus() != PaymentStatus.CANCELED) {
            throw new BusinessException(ErrorCode.PAYMENT_DELETE_FAILED);
        }

        if (payment.getOrders() != null) {
            payment.getOrders().removePayment();
        }

        paymentRepository.delete(payment);
    }

    // 취소된 결제 내역 삭제 - orderService 연동
    @Transactional(propagation = Propagation.MANDATORY)
    public void deletePaymentByOrder(Orders order, UserDto user) {
        Payment payment = order.getPayment();

        if (payment.getPaymentStatus() != PaymentStatus.CANCELED) {
            throw new BusinessException(ErrorCode.PAYMENT_DELETE_FAILED);
        }

        paymentRepository.delete(payment);
        order.removePayment();
    }
}
