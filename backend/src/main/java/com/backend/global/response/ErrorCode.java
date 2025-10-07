package com.backend.global.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** * 도메인별 발생할 Error Code 담당 enum */
@Getter
public enum ErrorCode {

    // 회원
    CONFLICT_REGISTER("U001", HttpStatus.CONFLICT, "이미 가입된 회원입니다."),
    NOT_FOUND_MEMBER("U002", HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    BAD_CREDENTIAL("U004", HttpStatus.UNAUTHORIZED, "아이디나 비밀번호가 틀렸습니다."),
    INVALID_TOKEN("U005", HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN("U006", HttpStatus.UNAUTHORIZED, "엑세스 토큰이 만료되었습니다. 토큰을 갱신해주세요."),
    NOT_LOGIN_ACCESS("U007", HttpStatus.UNAUTHORIZED, "로그인되어 있지 않습니다. 로그인 해 주십시오."),

    // 주소
    NOT_FOUND_ADDRESS("A001",HttpStatus.NOT_FOUND,"해당 주소를 찾을 수 없습니다."),

    // 장바구니
    NOT_FOUND_PRODUCT("C001", HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다."),
    SOLD_OUT_ADD_CART("C002", HttpStatus.CONFLICT, "품절된 상품은 장바구니에 담을 수 없습니다."),
    INVALID_QUANTITY("C004", HttpStatus.BAD_REQUEST, "상품 수량은 1개 이상이어야 합니다."),
    EMPTY_CART("C006", HttpStatus.NOT_FOUND, "장바구니가 비어 있습니다."),

    //주문
    SOLD_OUT_PRODUCT("O001", HttpStatus.CONFLICT, "품절된 상품이 포함되어 있습니다."),
    INVALID_ORDER_PRICE("O002", HttpStatus.BAD_REQUEST, "주문 가격이 올바르지 않습니다."),
    INVALID_ORDER_AMOUNT("O003", HttpStatus.BAD_REQUEST, "주문 총액이 올바르지 않습니다."),
    NOT_FOUND_ORDER("O004", HttpStatus.NOT_FOUND, "존재하지 않는 주문입니다."),
    INVALID_ORDER_STATUS("O005", HttpStatus.BAD_REQUEST, "알맞는 상태를 입력해주세요."),
    INVALID_STATUS_TRANSITION("O006", HttpStatus.BAD_REQUEST, "주문 상태 변경이 논리적으로 불가능합니다."),
    INVALID_ORDER_PROCESSING_TIME("O007", HttpStatus.BAD_REQUEST, "주문 상태는 매일 오후 2시에 일괄 처리됩니다."),

    // 결제
    PAYMENT_AMOUNT_INVALID("P001", HttpStatus.BAD_REQUEST, "결제 금액이 유효하지 않습니다."),
    PAYMENT_ALREADY_COMPLETED("P002", HttpStatus.CONFLICT, "이미 완료된 결제입니다."),
    PAYMENT_FAILED("P003", HttpStatus.PAYMENT_REQUIRED, "결제 처리에 실패했습니다."),
    NOT_FOUND_PAYMENT("P004", HttpStatus.NOT_FOUND, "존재하지 않는 결제입니다."),
    PAYMENT_ALREADY_CANCELLED("P005", HttpStatus.CONFLICT, "이미 취소된 결제입니다."),
    INVALID_PAYMENT_METHOD("P006", HttpStatus.BAD_REQUEST, "유효하지 않은 결제 방법입니다."),
    PAYMENT_AMOUNT_MISMATCH("P007", HttpStatus.BAD_REQUEST, "주문 금액과 결제 금액이 일치하지 않습니다."),
    PAYMENT_NOT_CANCELLABLE("P008", HttpStatus.CONFLICT, "취소할 수 없는 결제 상태입니다."),
    PAYMENT_DELETE_FAILED("P009", HttpStatus.BAD_REQUEST, "취소된 결제 내역만 삭제 가능합니다."),
    PAYMENT_INVALID_STATUS_TRANSITION("P010", HttpStatus.CONFLICT, "결제 상태 변경에 실패했습니다."),

    // 메뉴
    DUPLICATE_MENU_NAME("M001", HttpStatus.CONFLICT, "이미 존재하는 메뉴 이름입니다."),
    NOT_FOUND_MENU("M002", HttpStatus.NOT_FOUND, "존재하지 않는 메뉴입니다."),
    FORBIDDEN_ADMIN("M003", HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
    MENU_SOLD_OUT("M004", HttpStatus.CONFLICT, "품절된 메뉴입니다."),
    INVALID_MENU_PRICE("M005", HttpStatus.BAD_REQUEST, "메뉴 가격은 음수일 수 없습니다.");
  
    private final String code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}

