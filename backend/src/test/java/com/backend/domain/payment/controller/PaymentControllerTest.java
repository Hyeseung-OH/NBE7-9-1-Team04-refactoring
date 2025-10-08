package com.backend.domain.payment.controller;

import com.backend.domain.order.entity.OrderStatus;
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
import com.backend.domain.payment.service.PaymentProcessor;
import com.backend.domain.payment.service.PaymentService;
import com.backend.domain.user.address.dto.AddressDto;
import com.backend.domain.user.address.entity.Address;
import com.backend.domain.user.address.repository.AddressRepository;
import com.backend.domain.user.user.dto.UserDto;
import com.backend.domain.user.user.entity.Users;
import com.backend.domain.user.user.repository.UserRepository;
import com.backend.global.exception.BusinessException;
import com.backend.global.response.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * PaymentController 통합 테스트
 * Service 계층을 직접 호출하여 테스트 (MockMvc 대신)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentControllerTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private PaymentProcessor paymentProcessor;

    private Users testUser;
    private Address testAddress;
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(
                new Users("test@example.com", "password123", "010-1234-5678", 1)
        );

        AddressDto addressDto = new AddressDto(
                null,
                testUser.getUserId(),
                "서울시 강남구 테헤란로",
                "123번길 456호",
                "12345"
        );

        testAddress = addressRepository.save(new Address(testUser, addressDto));
        userDto = new UserDto(testUser);
    }

    @Test
    @DisplayName("결제 생성 성공")
    void createPayment_Success() {
        // Given
        Orders order = orderRepository.save(
                new Orders(testUser, 10000, OrderStatus.CREATED, testAddress)
        );

        PaymentCreateRequest request = new PaymentCreateRequest(
                order.getOrderId(),
                10000,
                PaymentMethod.CARD
        );

        when(paymentProcessor.process(any())).thenReturn(true);

        // When
        PaymentCreateResponse response = paymentService.createPayment(request, userDto);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isNotNull();
        assertThat(response.orderId()).isEqualTo(order.getOrderId());
        assertThat(response.paymentAmount()).isEqualTo(10000);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CARD);
    }

    @Test
    @DisplayName("결제 생성 실패 - 금액 불일치")
    void createPayment_Fail_AmountMismatch() {
        // Given
        Orders order = orderRepository.save(
                new Orders(testUser, 10000, OrderStatus.CREATED, testAddress)
        );

        PaymentCreateRequest request = new PaymentCreateRequest(
                order.getOrderId(),
                5000,
                PaymentMethod.CARD
        );

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.createPayment(request, userDto));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("결제 생성 실패 - 중복 결제")
    void createPayment_Fail_DuplicatePayment() {
        // Given
        Orders order = orderRepository.save(
                new Orders(testUser, 10000, OrderStatus.PAID, testAddress)
        );

        Payment existingPayment = new Payment(10000, PaymentMethod.CARD, order);
        existingPayment.complete();
        paymentRepository.save(existingPayment);
        order.updatePayment(existingPayment);

        PaymentCreateRequest request = new PaymentCreateRequest(
                order.getOrderId(),
                10000,
                PaymentMethod.CARD
        );

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.createPayment(request, userDto));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("결제 조회 성공")
    void getPayment_Success() {
        // Given
        Orders order = orderRepository.save(
                new Orders(testUser, 10000, OrderStatus.PAID, testAddress)
        );

        Payment payment = new Payment(10000, PaymentMethod.CARD, order);
        payment.complete();
        Payment savedPayment = paymentRepository.save(payment);
        order.updatePayment(savedPayment);

        // When
        PaymentInquiryResponse response = paymentService.getPayment(savedPayment.getPaymentId(), userDto);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo(savedPayment.getPaymentId());
        assertThat(response.orderId()).isEqualTo(order.getOrderId());
        assertThat(response.paymentAmount()).isEqualTo(10000);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("결제 조회 실패 - 존재하지 않는 결제")
    void getPayment_Fail_NotFound() {
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.getPayment(999L, userDto));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_PAYMENT);
    }

    @Test
    @DisplayName("결제 취소 성공")
    void cancelPayment_Success() {
        // Given
        Orders order = orderRepository.save(
                new Orders(testUser, 10000, OrderStatus.PAID, testAddress)
        );

        Payment payment = new Payment(10000, PaymentMethod.CARD, order);
        payment.complete();
        Payment savedPayment = paymentRepository.save(payment);
        order.updatePayment(savedPayment);

        // When
        PaymentCancelResponse response = paymentService.cancelPayment(savedPayment.getPaymentId(), userDto);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.CANCELED);

        Payment canceledPayment = paymentRepository.findById(savedPayment.getPaymentId()).orElseThrow();
        assertThat(canceledPayment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    @DisplayName("결제 취소 실패 - 이미 취소된 결제")
    void cancelPayment_Fail_AlreadyCanceled() {
        // Given
        Orders order = orderRepository.save(
                new Orders(testUser, 10000, OrderStatus.CANCELED, testAddress)
        );

        Payment payment = new Payment(10000, PaymentMethod.CARD, order);
        payment.complete();
        payment.cancel();
        Payment savedPayment = paymentRepository.save(payment);
        order.updatePayment(savedPayment);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.cancelPayment(savedPayment.getPaymentId(), userDto));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_CANCELLED);
    }

    @Test
    @DisplayName("결제 취소 실패 - 완료되지 않은 결제")
    void cancelPayment_Fail_NotCompleted() {
        // Given
        Orders order = orderRepository.save(
                new Orders(testUser, 10000, OrderStatus.CREATED, testAddress)
        );

        Payment payment = new Payment(10000, PaymentMethod.CARD, order);
        Payment savedPayment = paymentRepository.save(payment);
        order.updatePayment(savedPayment);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.cancelPayment(savedPayment.getPaymentId(), userDto));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_CANCELLABLE);
    }

    @Test
    @DisplayName("결제 삭제 성공")
    void deletePayment_Success() {
        // Given
        Orders order = orderRepository.save(
                new Orders(testUser, 10000, OrderStatus.CANCELED, testAddress)
        );

        Payment payment = new Payment(10000, PaymentMethod.CARD, order);
        payment.complete();
        payment.cancel();
        Payment savedPayment = paymentRepository.save(payment);
        order.updatePayment(savedPayment);

        // When
        paymentService.deletePayment(savedPayment.getPaymentId(), userDto);

        // Then
        assertThat(paymentRepository.findById(savedPayment.getPaymentId())).isEmpty();
    }

    @Test
    @DisplayName("결제 삭제 실패 - 취소되지 않은 결제")
    void deletePayment_Fail_NotCanceled() {
        // Given
        Orders order = orderRepository.save(
                new Orders(testUser, 10000, OrderStatus.PAID, testAddress)
        );

        Payment payment = new Payment(10000, PaymentMethod.CARD, order);
        payment.complete();
        Payment savedPayment = paymentRepository.save(payment);
        order.updatePayment(savedPayment);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.deletePayment(savedPayment.getPaymentId(), userDto));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_DELETE_FAILED);
    }

    @Test
    @DisplayName("존재하지 않는 주문 결제 시도 실패")
    void createPayment_Fail_OrderNotFound() {
        // Given
        PaymentCreateRequest request = new PaymentCreateRequest(
                999L,
                10000,
                PaymentMethod.CARD
        );

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.createPayment(request, userDto));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_ORDER);
    }
}
