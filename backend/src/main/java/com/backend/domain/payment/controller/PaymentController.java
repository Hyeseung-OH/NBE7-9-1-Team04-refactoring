package com.backend.domain.payment.controller;

import com.backend.domain.payment.dto.request.PaymentCreateRequest;
import com.backend.domain.payment.dto.response.PaymentCancelResponse;
import com.backend.domain.payment.dto.response.PaymentCreateResponse;
import com.backend.domain.payment.dto.response.PaymentInquiryResponse;
import com.backend.domain.payment.service.PaymentService;
import com.backend.domain.user.user.dto.UserDto;
import com.backend.global.response.ApiResponse;
import com.backend.global.rq.Rq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "결제 API")
public class PaymentController {
    private final PaymentService paymentService;
    private final Rq rq;

    // 결제 생성 API
    @Operation(
            summary = "결제 생성 및 처리",
            description = "주문에 대한 결제를 생성하고 MOCK 방식으로 즉시 승인 처리합니다. " +
                    "결제 완료 시 주문 상태가 PAID로 변경됩니다."
    )
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<PaymentCreateResponse>> createPayment(
            @Valid @RequestBody PaymentCreateRequest request
            ) throws Exception {
        UserDto currentUser = rq.getUser();
        PaymentCreateResponse response = paymentService.createPayment(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 결제 단건 조회 API
    @Operation(
            summary = "결제 상세 정보 조회",
            description = "결제 ID를 통해 결제의 상세 정보(금액, 방법, 상태, 생성일시 등)를 조회합니다."
    )
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentInquiryResponse>> getPayment(
            @PathVariable @Positive(message = "결제 ID는 양수여야 합니다.") Long paymentId
    ) throws Exception {
        UserDto currentUser = rq.getUser();
        PaymentInquiryResponse response = paymentService.getPayment(paymentId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 결제 단건 취소 API
    @Operation(
            summary = "결제 취소",
            description = "완료(COMPLETED)된 결제를 취소(CANCELED) 상태로 변경합니다. " +
                    "이미 취소된 결제나 취소 불가능한 상태의 결제는 에러를 반환합니다."
    )
    @PutMapping("/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<PaymentCancelResponse>> cancelPayment(
            @PathVariable @Positive(message = "결제 ID는 양수여야 합니다.") Long paymentId
    ) throws Exception {
        UserDto currentUser = rq.getUser();
        PaymentCancelResponse response = paymentService.cancelPayment(paymentId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 취소된 결제 내역 삭제
    @Operation(
            summary = "결제 삭제",
            description = "취소(CANCELED) 상태의 결제 내역만 삭제합니다. " +
                    "취소되지 않았거나, 존재하지 않는 paymentId에 대한 삭제를 요청할 시 에러를 반환합니다."
    )
    @DeleteMapping("/{paymentId}/delete")
    public ResponseEntity<ApiResponse<Void>> deletePayment(
            @PathVariable @Positive(message = "결제 ID는 양수여야 합니다.") Long paymentId
    ) throws Exception {
        UserDto currentUser = rq.getUser();
        paymentService.deletePayment(paymentId, currentUser);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
