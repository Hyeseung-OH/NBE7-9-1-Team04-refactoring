package com.backend.domain.payment.service;

import com.backend.domain.order.entity.OrderStatus;
import com.backend.domain.order.entity.Orders;
import com.backend.domain.order.repository.OrderRepository;
import com.backend.domain.order.service.OrderService;
import com.backend.domain.payment.dto.request.PaymentCreateRequest;
import com.backend.domain.payment.dto.response.PaymentCreateResponse;
import com.backend.domain.payment.entity.Payment;
import com.backend.domain.payment.entity.PaymentMethod;
import com.backend.domain.payment.entity.PaymentStatus;
import com.backend.domain.payment.repository.PaymentRepository;
import com.backend.domain.user.address.dto.AddressDto;
import com.backend.domain.user.address.entity.Address;
import com.backend.domain.user.address.repository.AddressRepository;
import com.backend.domain.user.user.dto.UserDto;
import com.backend.domain.user.user.entity.Users;
import com.backend.domain.user.user.repository.UserRepository;
import com.backend.global.exception.BusinessException;
import com.backend.global.response.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class PaymentServiceTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentFactory paymentFactory;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @MockBean
    private PaymentProcessor paymentProcessor;

    @Test
    @DisplayName("결제 성공 테스트 - 단순화 (MockProcessor 사용)")
    void createPayment_Success_Simple() {
        // Given
        Users savedUser = userRepository.save(
                new Users("test@example.com", "password123", "010-1234-5678", 1)
        );

        AddressDto addressDto = new AddressDto(
                null,
                savedUser.getUserId(),
                "서울시 강남구 테헤란로",
                "123번길 456호",
                "12345"
        );

        Address savedAddress = addressRepository.save(
                new Address(savedUser, addressDto)
        );

        Orders savedOrder = orderRepository.save(
                new Orders(savedUser, 10000, OrderStatus.CREATED, savedAddress)
        );

        PaymentCreateRequest paymentRequest = new PaymentCreateRequest(
                savedOrder.getOrderId(), 10000, PaymentMethod.CARD
        );
        UserDto userDto = new UserDto(savedUser);

        // 무조건 성공하도록
        when(paymentProcessor.process(any())).thenReturn(true);

        // When
        PaymentCreateResponse response = paymentService.createPayment(paymentRequest, userDto);

        // Then
        Orders updatedOrder = orderRepository.findById(savedOrder.getOrderId()).orElseThrow();

        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isNotNull();
        assertThat(response.paymentAmount()).isEqualTo(10000);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CARD);

        // 결제 성공 상태 검증
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(updatedOrder.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(updatedOrder.getPayment()).isNotNull();

        // 실제 저장된 Payment 객체의 상태도 확인
        Payment savedPayment = paymentRepository.findById(response.paymentId()).orElseThrow();
        assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("결제 실패 테스트")
    void createPayment_Failure_Simple() {
        // Given
        Users savedUser = userRepository.save(
                new Users("fail@example.com", "password123", "010-9876-5432", 1)
        );

        AddressDto addressDto = new AddressDto(
                null,
                savedUser.getUserId(),
                "서울시 강남구 테헤란로",
                "123번길 456호",
                "12345"
        );

        Address savedAddress = addressRepository.save(
                new Address(savedUser, addressDto)
        );

        Orders savedOrder = orderRepository.save(
                new Orders(savedUser, 20000, OrderStatus.CREATED, savedAddress)
        );

        PaymentCreateRequest paymentRequest = new PaymentCreateRequest(
                savedOrder.getOrderId(), 20000, PaymentMethod.CARD
        );
        UserDto userDto = new UserDto(savedUser);

        // 무조건 실패하도록
        when(paymentProcessor.process(any())).thenReturn(false);

        // When & Then
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> paymentService.createPayment(paymentRequest, userDto)
        );

        // 예외 객체와 ErrorCode 검증
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_FAILED);

        // 디버깅용 출력
        System.out.println("예외 클래스 = " + ex.getClass().getName());
        System.out.println("ErrorCode = " + ex.getErrorCode());

        // DB 상태 검증
        List<Payment> payments = paymentRepository.findAll();
        assertThat(payments).isNotEmpty();
        assertThat(payments.get(0).getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);

        Orders updatedOrder = orderRepository.findById(savedOrder.getOrderId()).orElseThrow();
        assertThat(updatedOrder.getOrderStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(updatedOrder.getPayment()).isNotNull();
    }

    // 성공 케이스: CANCELED 상태의 결제 삭제
    @Test
    @DisplayName("취소된 결제 내역 삭제 성공")
    void deletePaymentByOrder_Success() {
        // Given
        Users savedUser = userRepository.save(new Users("test@example.com", "1234", "010-1234-5678", 1));

        AddressDto addressDto = new AddressDto(
                null,
                savedUser.getUserId(),
                "서울시 강남구 테헤란로",
                "123번길 456호",
                "12345"
        );

        Address savedAddress = addressRepository.save(
                new Address(savedUser, addressDto)
        );

        Orders order = orderRepository.save(new Orders(savedUser, 10000, OrderStatus.CANCELED, savedAddress));

        Payment payment = new Payment(10000, PaymentMethod.CARD, order);
        payment.complete(); // 먼저 완료 상태로 변경
        payment.cancel();   // 그 다음 취소
        paymentRepository.save(payment);

        order.updatePayment(payment);

        // When
        paymentService.deletePaymentByOrder(order, new UserDto(savedUser));

        // Then
        assertThat(paymentRepository.findById(payment.getPaymentId())).isEmpty();
        assertThat(order.getPayment()).isNull();
    }

    // 실패 케이스: COMPLETED 상태의 결제는 삭제 불가
    @Test
    @DisplayName("취소되지 않은 결제 내역 삭제 실패")
    void deletePaymentByOrder_Fail_NotCanceled() {
        // Given
        Users savedUser = userRepository.save(new Users("fail@example.com", "1234", "010-1111-2222", 1));

        AddressDto addressDto = new AddressDto(
                null,
                savedUser.getUserId(),
                "서울시 강남구 테헤란로",
                "123번길 456호",
                "12345"
        );

        Address savedAddress = addressRepository.save(
                new Address(savedUser, addressDto)
        );

        Orders order = orderRepository.save(new Orders(savedUser, 20000, OrderStatus.PAID, savedAddress));

        Payment payment = new Payment(20000, PaymentMethod.CARD, order);
        payment.complete();
        paymentRepository.save(payment);

        order.updatePayment(payment);

        // When & Then
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> paymentService.deletePaymentByOrder(order, new UserDto(savedUser))
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_DELETE_FAILED);
    }
}