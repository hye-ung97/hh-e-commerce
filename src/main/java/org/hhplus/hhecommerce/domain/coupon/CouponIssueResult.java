package org.hhplus.hhecommerce.domain.coupon;

public enum CouponIssueResult {

    SUCCESS("쿠폰이 발급되었습니다"),
    ALREADY_ISSUED("이미 발급받은 쿠폰입니다"),
    OUT_OF_STOCK("쿠폰이 모두 소진되었습니다"),
    COUPON_NOT_FOUND("쿠폰을 찾을 수 없습니다"),
    NOT_AVAILABLE("발급 기간이 아닙니다"),
    ISSUE_FAILED("쿠폰 발급에 실패했습니다"),
    LOCK_ACQUISITION_FAILED("잠시 후 다시 시도해주세요"),
    PENDING_IN_PROGRESS("처리 중입니다. 잠시 후 다시 시도해주세요");

    private final String message;

    CouponIssueResult(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
