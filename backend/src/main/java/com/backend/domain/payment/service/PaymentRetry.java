package com.backend.domain.payment.service;

import com.backend.domain.order.entity.OrderStatus;
import com.backend.domain.order.entity.Orders;
import com.backend.domain.order.repository.OrderRepository;
import com.backend.domain.payment.dto.request.PaymentCreateRequest;
import com.backend.domain.payment.dto.response.PaymentCreateResponse;
import com.backend.domain.payment.entity.Payment;
import com.backend.domain.payment.repository.PaymentRepository;
import com.backend.global.exception.BusinessException;
import com.backend.global.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRetry {
    private final PaymentProcessor paymentProcessor;
    private final PaymentFactory paymentFactory;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /**
     * 재시도 로직
     * 결제 처리기 (PaymentProcessor) 호출 실패 시 최대 2회 재시도
     * BusinessException, RuntimeException 발생 시 재시도
     * IllegalArgumentException은 재시도하지 않음 (잘못된 파라미터는 재시도해도 의미 없음)
     * */
    @Retryable(
            retryFor = {BusinessException.class, RuntimeException.class},
            noRetryFor = {IllegalArgumentException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 1000)
    )
    public PaymentCreateResponse processPaymentWithRetry(PaymentCreateRequest request, Orders orders) {
        boolean paymentSuccess = paymentProcessor.process(request);

        if (!paymentSuccess) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }

        Payment payment = paymentFactory.createCompletedPayment(request, orders);
        Payment savePayment = paymentRepository.save(payment);

        orders.updateOrderStatus(OrderStatus.PAID);
        orderRepository.save(orders);

        return new PaymentCreateResponse(savePayment);
    }

    /**
     * 모든 재시도 실패 시 실행되는 복구 메서드
     * - @Retryable 메서드의 모든 재시도가 실패했을 때 자동 호출
     */
    @Recover
    public PaymentCreateResponse recoverFromPaymentFailure(Exception ex,
                                                           PaymentCreateRequest request,
                                                           Orders orders) {
        Payment failedPayment = paymentFactory.createFailedPayment(request, orders);
        paymentRepository.save(failedPayment);

        orderRepository.save(orders);

        throw new BusinessException(ErrorCode.PAYMENT_FAILED);
    }
}