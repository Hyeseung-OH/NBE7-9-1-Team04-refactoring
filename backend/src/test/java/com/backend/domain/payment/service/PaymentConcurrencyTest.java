package com.backend.domain.payment.service;

import com.backend.domain.order.entity.OrderStatus;
import com.backend.domain.order.entity.Orders;
import com.backend.domain.order.repository.OrderRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 결제 동시성 제어 테스트
 * - 낙관적 락 (@Version)
 * - DB 유니크 제약 조건
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentConcurrencyTest {

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

    @Test
    @DisplayName("동시 결제 시도 - 낙관적 락으로 중복 방지")
    void concurrentPayment_OptimisticLock() throws InterruptedException {
        // Given
        Users savedUser = userRepository.save(
                new Users("concurrent@example.com", "password123", "010-1234-5678", 1)
        );

        AddressDto addressDto = new AddressDto(
                null,
                savedUser.getUserId(),
                "서울시 강남구 테헤란로",
                "123번길 456호",
                "12345"
        );

        Address savedAddress = addressRepository.save(new Address(savedUser, addressDto));

        Orders savedOrder = orderRepository.save(
                new Orders(savedUser, 10000, OrderStatus.CREATED, savedAddress)
        );

        PaymentCreateRequest request = new PaymentCreateRequest(
                savedOrder.getOrderId(),
                10000,
                PaymentMethod.CARD
        );

        UserDto userDto = new UserDto(savedUser);

        when(paymentProcessor.process(any())).thenReturn(true);

        // When - 10개의 스레드가 동시에 같은 주문에 대해 결제 시도
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    paymentService.createPayment(request, userDto);
                    successCount.incrementAndGet();
                } catch (BusinessException | DataIntegrityViolationException e) {
                    failCount.incrementAndGet();
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then
        System.out.println("성공 횟수: " + successCount.get());
        System.out.println("실패 횟수: " + failCount.get());
        System.out.println("예외 목록: " + exceptions.size());

        // 정확히 1개만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        // DB에 정확히 1개의 결제만 저장되어야 함
        List<Payment> payments = paymentRepository.findAll();
        long completedPayments = payments.stream()
                .filter(p -> p.getOrders().getOrderId().equals(savedOrder.getOrderId()))
                .filter(p -> p.getPaymentStatus() == PaymentStatus.COMPLETED)
                .count();

        assertThat(completedPayments).isEqualTo(1);
    }

    @Test
    @DisplayName("동시 결제 시도 - 유니크 제약 조건으로 중복 방지")
    void concurrentPayment_UniqueConstraint() throws InterruptedException {
        // Given
        Users savedUser = userRepository.save(
                new Users("unique@example.com", "password123", "010-1234-5678", 1)
        );

        AddressDto addressDto = new AddressDto(
                null,
                savedUser.getUserId(),
                "서울시 강남구 테헤란로",
                "123번길 456호",
                "12345"
        );

        Address savedAddress = addressRepository.save(new Address(savedUser, addressDto));

        Orders savedOrder = orderRepository.save(
                new Orders(savedUser, 20000, OrderStatus.CREATED, savedAddress)
        );

        PaymentCreateRequest request = new PaymentCreateRequest(
                savedOrder.getOrderId(),
                20000,
                PaymentMethod.CARD
        );

        UserDto userDto = new UserDto(savedUser);

        when(paymentProcessor.process(any())).thenReturn(true);

        // When - 5개의 스레드가 동시에 결제 시도
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    paymentService.createPayment(request, userDto);
                    return true;
                } catch (Exception e) {
                    return false;
                } finally {
                    latch.countDown();
                }
            }, executorService);
            futures.add(future);
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then
        long successCount = futures.stream()
                .map(CompletableFuture::join)
                .filter(result -> result)
                .count();

        System.out.println("유니크 제약 테스트 - 성공 횟수: " + successCount);

        // 1개만 성공해야 함
        assertThat(successCount).isEqualTo(1);

        // DB 검증
        List<Payment> allPayments = paymentRepository.findAll();
        long paymentsForOrder = allPayments.stream()
                .filter(p -> p.getOrders().getOrderId().equals(savedOrder.getOrderId()))
                .count();

        assertThat(paymentsForOrder).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 주문에 대한 동시 결제는 모두 성공")
    void concurrentPayment_DifferentOrders_AllSuccess() throws InterruptedException {
        // Given
        Users savedUser = userRepository.save(
                new Users("multi@example.com", "password123", "010-1234-5678", 1)
        );

        AddressDto addressDto = new AddressDto(
                null,
                savedUser.getUserId(),
                "서울시 강남구 테헤란로",
                "123번길 456호",
                "12345"
        );

        Address savedAddress = addressRepository.save(new Address(savedUser, addressDto));

        int orderCount = 5;
        List<Orders> orders = new ArrayList<>();
        for (int i = 0; i < orderCount; i++) {
            Orders order = orderRepository.save(
                    new Orders(savedUser, 10000 + i * 1000, OrderStatus.CREATED, savedAddress)
            );
            orders.add(order);
        }

        UserDto userDto = new UserDto(savedUser);

        when(paymentProcessor.process(any())).thenReturn(true);

        // When - 각 주문에 대해 1개씩 결제 시도 (총 5개 동시 실행)
        ExecutorService executorService = Executors.newFixedThreadPool(orderCount);
        CountDownLatch latch = new CountDownLatch(orderCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (Orders order : orders) {
            PaymentCreateRequest request = new PaymentCreateRequest(
                    order.getOrderId(),
                    order.getOrderAmount(),
                    PaymentMethod.CARD
            );

            executorService.submit(() -> {
                try {
                    PaymentCreateResponse response = paymentService.createPayment(request, userDto);
                    successCount.incrementAndGet();
                    System.out.println("결제 성공: orderId=" + response.orderId());
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("결제 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then - 모두 성공해야 함
        System.out.println("서로 다른 주문 테스트 - 성공: " + successCount.get() + ", 실패: " + failCount.get());

        assertThat(successCount.get()).isEqualTo(orderCount);
        assertThat(failCount.get()).isEqualTo(0);

        // DB 검증
        List<Payment> payments = paymentRepository.findAll();
        long completedPayments = payments.stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.COMPLETED)
                .count();

        assertThat(completedPayments).isGreaterThanOrEqualTo(orderCount);
    }

    @Test
    @DisplayName("동시 결제 취소 시도 - 낙관적 락으로 중복 방지")
    void concurrentCancel_OptimisticLock() throws InterruptedException {
        // Given
        Users savedUser = userRepository.save(
                new Users("cancel@example.com", "password123", "010-1234-5678", 1)
        );

        AddressDto addressDto = new AddressDto(
                null,
                savedUser.getUserId(),
                "서울시 강남구 테헤란로",
                "123번길 456호",
                "12345"
        );

        Address savedAddress = addressRepository.save(new Address(savedUser, addressDto));

        Orders savedOrder = orderRepository.save(
                new Orders(savedUser, 30000, OrderStatus.PAID, savedAddress)
        );

        Payment payment = new Payment(30000, PaymentMethod.CARD, savedOrder);
        payment.complete();
        Payment savedPayment = paymentRepository.save(payment);
        savedOrder.updatePayment(savedPayment);

        UserDto userDto = new UserDto(savedUser);

        // When - 3개의 스레드가 동시에 같은 결제 취소 시도
        int threadCount = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    paymentService.cancelPayment(savedPayment.getPaymentId(), userDto);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then - Payment의 @Version 낙관적 락으로 1개만 성공
        System.out.println("동시 취소 테스트 - 성공: " + successCount.get() + ", 실패: " + failCount.get());

        // 정확히 1개만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        // DB 상태 확인 - 최종적으로는 취소 상태여야 함
        Payment canceledPayment = paymentRepository.findById(savedPayment.getPaymentId()).orElseThrow();
        assertThat(canceledPayment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    @DisplayName("대량 동시 결제 요청 - 성능 및 안정성 테스트")
    void massiveConcurrentPayment_StressTest() throws InterruptedException {
        // Given
        Users savedUser = userRepository.save(
                new Users("stress@example.com", "password123", "010-1234-5678", 1)
        );

        AddressDto addressDto = new AddressDto(
                null,
                savedUser.getUserId(),
                "서울시 강남구 테헤란로",
                "123번길 456호",
                "12345"
        );

        Address savedAddress = addressRepository.save(new Address(savedUser, addressDto));

        Orders savedOrder = orderRepository.save(
                new Orders(savedUser, 50000, OrderStatus.CREATED, savedAddress)
        );

        PaymentCreateRequest request = new PaymentCreateRequest(
                savedOrder.getOrderId(),
                50000,
                PaymentMethod.CARD
        );

        UserDto userDto = new UserDto(savedUser);

        when(paymentProcessor.process(any())).thenReturn(true);

        // When - 50개의 스레드가 동시에 결제 시도
        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    paymentService.createPayment(request, userDto);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        System.out.println("=== 대량 동시 결제 스트레스 테스트 결과 ===");
        System.out.println("총 요청 수: " + threadCount);
        System.out.println("성공 횟수: " + successCount.get());
        System.out.println("실패 횟수: " + failCount.get());
        System.out.println("소요 시간: " + duration + "ms");

        // 정확히 1개만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        // DB 무결성 확인
        List<Payment> payments = paymentRepository.findAll();
        long paymentsForOrder = payments.stream()
                .filter(p -> p.getOrders().getOrderId().equals(savedOrder.getOrderId()))
                .filter(p -> p.getPaymentStatus() == PaymentStatus.COMPLETED)
                .count();

        assertThat(paymentsForOrder).isEqualTo(1);
    }
}
