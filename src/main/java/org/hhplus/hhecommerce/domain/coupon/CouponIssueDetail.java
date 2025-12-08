package org.hhplus.hhecommerce.domain.coupon;

/**
 * 쿠폰 발급 결과와 상세 정보를 담는 record.
 *
 * <p>Lua 스크립트에서 반환된 상세 정보를 클라이언트에 전달할 수 있습니다.</p>
 *
 * @param result 발급 결과
 * @param remainingStock 남은 재고 (-1: 정보 없음)
 * @param elapsedMs pending 경과 시간 (밀리초, -1: 정보 없음)
 * @param debugMessage 디버그용 메시지 (null 가능)
 */
public record CouponIssueDetail(
        CouponIssueResult result,
        int remainingStock,
        long elapsedMs,
        String debugMessage
) {

    /**
     * 결과만 포함하는 간단한 인스턴스 생성.
     */
    public static CouponIssueDetail of(CouponIssueResult result) {
        return new CouponIssueDetail(result, -1, -1, null);
    }

    /**
     * 결과와 남은 재고를 포함하는 인스턴스 생성.
     */
    public static CouponIssueDetail withStock(CouponIssueResult result, int remainingStock) {
        return new CouponIssueDetail(result, remainingStock, -1, null);
    }

    /**
     * 결과와 경과 시간을 포함하는 인스턴스 생성.
     */
    public static CouponIssueDetail withElapsed(CouponIssueResult result, long elapsedMs) {
        return new CouponIssueDetail(result, -1, elapsedMs, null);
    }

    /**
     * 모든 정보를 포함하는 인스턴스 생성.
     */
    public static CouponIssueDetail full(CouponIssueResult result, int remainingStock,
                                         long elapsedMs, String debugMessage) {
        return new CouponIssueDetail(result, remainingStock, elapsedMs, debugMessage);
    }

    public boolean isSuccess() {
        return result.isSuccess();
    }

    public String getCode() {
        return result.getCode();
    }

    public String getMessage() {
        return result.getMessage();
    }
}
