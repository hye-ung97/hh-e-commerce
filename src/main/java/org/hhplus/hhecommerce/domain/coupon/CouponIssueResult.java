package org.hhplus.hhecommerce.domain.coupon;

/**
 * 쿠폰 발급 결과를 나타내는 enum.
 *
 * <p>각 결과는 고유한 에러 코드와 사용자 친화적 메시지를 가집니다.</p>
 */
public enum CouponIssueResult {

    SUCCESS("COUPON_001", "쿠폰이 발급되었습니다"),
    ALREADY_ISSUED("COUPON_002", "이미 발급받은 쿠폰입니다"),
    OUT_OF_STOCK("COUPON_003", "쿠폰이 모두 소진되었습니다"),
    COUPON_NOT_FOUND("COUPON_004", "쿠폰을 찾을 수 없습니다"),
    NOT_AVAILABLE("COUPON_005", "발급 기간이 아닙니다"),
    ISSUE_FAILED("COUPON_006", "쿠폰 발급에 실패했습니다"),
    LOCK_ACQUISITION_FAILED("COUPON_007", "잠시 후 다시 시도해주세요"),
    PENDING_IN_PROGRESS("COUPON_008", "처리 중입니다. 잠시 후 다시 시도해주세요");

    private final String code;
    private final String message;

    CouponIssueResult(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
