package org.hhplus.hhecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hhplus.hhecommerce.dto.coupon.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Tag(name = "Coupon", description = "쿠폰 관리 API")
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private static final AtomicLong USER_COUPON_ID_GENERATOR = new AtomicLong(1);

    private static final Map<Long, Map<String, Object>> COUPONS = Map.of(
        1L, createCoupon(1L, "신규가입 10% 할인", "PERCENTAGE", 10, 100000, 100, 50,
            LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30)),
        2L, createCoupon(2L, "5만원 할인쿠폰", "FIXED", 50000, 200000, 50, 30,
            LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(15)),
        3L, createCoupon(3L, "노트북 20% 특별할인", "PERCENTAGE", 20, 500000, 30, 5,
            LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7)),
        4L, createCoupon(4L, "1만원 할인쿠폰", "FIXED", 10000, 50000, 200, 150,
            LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(60)),
        5L, createCoupon(5L, "VIP 15% 할인", "PERCENTAGE", 15, 300000, 20, 20,
            LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(30))  // 아직 시작 안됨
    );

    private static final Map<Long, Map<String, Object>> USER_COUPONS = new ConcurrentHashMap<>();

    private static Map<String, Object> createCoupon(Long id, String name, String discountType,
                                                    int discountValue, int minOrderAmount,
                                                    int totalQuantity, int issuedQuantity,
                                                    LocalDateTime startAt, LocalDateTime endAt) {
        Map<String, Object> coupon = new HashMap<>();
        coupon.put("id", id);
        coupon.put("name", name);
        coupon.put("discountType", discountType);
        coupon.put("discountValue", discountValue);
        coupon.put("minOrderAmount", minOrderAmount);
        coupon.put("totalQuantity", totalQuantity);
        coupon.put("issuedQuantity", issuedQuantity);
        coupon.put("remainingQuantity", totalQuantity - issuedQuantity);
        coupon.put("startAt", startAt);
        coupon.put("endAt", endAt);
        return coupon;
    }

    @Operation(summary = "발급 가능한 쿠폰 목록 조회", description = "현재 발급 가능한 쿠폰 목록을 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CouponListResponse.class)
            )
        )
    })
    @GetMapping
    public CouponListResponse getAvailableCoupons() {
        LocalDateTime now = LocalDateTime.now();

        List<CouponListResponse.CouponInfo> availableCoupons = COUPONS.values().stream()
            .filter(coupon -> {
                LocalDateTime startAt = (LocalDateTime) coupon.get("startAt");
                LocalDateTime endAt = (LocalDateTime) coupon.get("endAt");
                int issuedQuantity = (Integer) coupon.get("issuedQuantity");
                int totalQuantity = (Integer) coupon.get("totalQuantity");

                return !now.isBefore(startAt) && now.isBefore(endAt) && issuedQuantity < totalQuantity;
            })
            .map(coupon -> CouponListResponse.CouponInfo.builder()
                .id((Long) coupon.get("id"))
                .name((String) coupon.get("name"))
                .discountType((String) coupon.get("discountType"))
                .discountValue((Integer) coupon.get("discountValue"))
                .minOrderAmount((Integer) coupon.get("minOrderAmount"))
                .remainingQuantity((Integer) coupon.get("totalQuantity") - (Integer) coupon.get("issuedQuantity"))
                .startAt((LocalDateTime) coupon.get("startAt"))
                .endAt((LocalDateTime) coupon.get("endAt"))
                .build())
            .toList();

        return CouponListResponse.builder()
            .coupons(availableCoupons)
            .totalCount(availableCoupons.size())
            .build();
    }

    @Operation(summary = "쿠폰 발급", description = "사용자에게 쿠폰을 발급합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "발급 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = IssueCouponResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "쿠폰 품절, 발급 기간 아님, 또는 이미 발급됨")
    })
    @PostMapping("/{couponId}/issue")
    public IssueCouponResponse issueCoupon(
        @Parameter(description = "쿠폰 ID", example = "1")
        @PathVariable Long couponId,
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId
    ) {
        Map<String, Object> coupon = COUPONS.get(couponId);
        if (coupon == null) {
            throw new RuntimeException("Coupon not found: " + couponId);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startAt = (LocalDateTime) coupon.get("startAt");
        LocalDateTime endAt = (LocalDateTime) coupon.get("endAt");

        // 발급 기간 확인
        if (now.isBefore(startAt)) {
            throw new RuntimeException("Coupon issuance has not started yet");
        }
        if (!now.isBefore(endAt)) {
            throw new RuntimeException("Coupon issuance period has ended");
        }

        // 재고 확인 (동시성 제어 없이 간단히 구현)
        synchronized (coupon) {
            int issuedQuantity = (Integer) coupon.get("issuedQuantity");
            int totalQuantity = (Integer) coupon.get("totalQuantity");

            if (issuedQuantity >= totalQuantity) {
                throw new RuntimeException("Coupon is sold out");
            }

            // 중복 발급 확인
            boolean alreadyIssued = USER_COUPONS.values().stream()
                .anyMatch(uc -> userId.equals(uc.get("userId")) && couponId.equals(uc.get("couponId")));

            if (alreadyIssued) {
                throw new RuntimeException("Coupon already issued to this user");
            }

            // 쿠폰 발급
            Long userCouponId = USER_COUPON_ID_GENERATOR.getAndIncrement();
            Map<String, Object> userCoupon = new HashMap<>();
            userCoupon.put("id", userCouponId);
            userCoupon.put("userId", userId);
            userCoupon.put("couponId", couponId);
            userCoupon.put("couponName", coupon.get("name"));
            userCoupon.put("discountType", coupon.get("discountType"));
            userCoupon.put("discountValue", coupon.get("discountValue"));
            userCoupon.put("minOrderAmount", coupon.get("minOrderAmount"));
            userCoupon.put("status", "AVAILABLE");
            userCoupon.put("issuedAt", now);
            userCoupon.put("expiredAt", endAt);

            USER_COUPONS.put(userCouponId, userCoupon);

            // 발급 수량 증가
            coupon.put("issuedQuantity", issuedQuantity + 1);
            coupon.put("remainingQuantity", totalQuantity - issuedQuantity - 1);

            return IssueCouponResponse.builder()
                .id(userCouponId)
                .userId(userId)
                .couponId(couponId)
                .couponName((String) coupon.get("name"))
                .discountType((String) coupon.get("discountType"))
                .discountValue((Integer) coupon.get("discountValue"))
                .minOrderAmount((Integer) coupon.get("minOrderAmount"))
                .isUsed(false)
                .issuedAt(now)
                .message("Coupon issued successfully")
                .build();
        }
    }

    @Operation(summary = "보유 쿠폰 조회", description = "사용자가 보유한 전체 쿠폰을 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserCouponListResponse.class)
            )
        )
    })
    @GetMapping("/users/coupons")
    public UserCouponListResponse getUserCoupons(
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId
    ) {
        List<UserCouponListResponse.UserCouponInfo> userCoupons = USER_COUPONS.values().stream()
            .filter(uc -> userId.equals(uc.get("userId")))
            .sorted((a, b) -> ((LocalDateTime) b.get("issuedAt")).compareTo((LocalDateTime) a.get("issuedAt")))
            .map(uc -> UserCouponListResponse.UserCouponInfo.builder()
                .id((Long) uc.get("id"))
                .userId((Long) uc.get("userId"))
                .couponId((Long) uc.get("couponId"))
                .couponName((String) uc.get("couponName"))
                .discountType((String) uc.get("discountType"))
                .discountValue((Integer) uc.get("discountValue"))
                .minOrderAmount((Integer) uc.get("minOrderAmount"))
                .isUsed("USED".equals(uc.get("status")))
                .issuedAt((LocalDateTime) uc.get("issuedAt"))
                .build())
            .toList();

        return UserCouponListResponse.builder()
            .coupons(userCoupons)
            .totalCount(userCoupons.size())
            .build();
    }

    @Operation(summary = "사용 가능한 쿠폰 조회", description = "특정 주문 금액에 사용 가능한 쿠폰을 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AvailableUserCouponListResponse.class)
            )
        )
    })
    @GetMapping("/users/coupons/available")
    public AvailableUserCouponListResponse getAvailableUserCoupons(
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId,
        @Parameter(description = "주문 금액 (선택사항)", example = "500000")
        @RequestParam(required = false) Integer orderAmount
    ) {
        LocalDateTime now = LocalDateTime.now();

        List<AvailableUserCouponListResponse.AvailableUserCouponInfo> availableCoupons = USER_COUPONS.values().stream()
            .filter(uc -> {
                if (!userId.equals(uc.get("userId"))) return false;
                if (!"AVAILABLE".equals(uc.get("status"))) return false;

                LocalDateTime expiredAt = (LocalDateTime) uc.get("expiredAt");
                if (!now.isBefore(expiredAt)) return false;

                // 주문 금액이 제공된 경우 최소 주문 금액 확인
                if (orderAmount != null) {
                    int minOrderAmount = (Integer) uc.get("minOrderAmount");
                    if (orderAmount < minOrderAmount) return false;
                }

                return true;
            })
            .map(uc -> {
                // 할인 금액 계산
                Integer expectedDiscount = null;
                Integer finalAmount = null;

                if (orderAmount != null) {
                    String discountType = (String) uc.get("discountType");
                    int discountValue = (Integer) uc.get("discountValue");
                    int discountAmount = 0;

                    if ("PERCENTAGE".equals(discountType)) {
                        discountAmount = orderAmount * discountValue / 100;
                    } else if ("FIXED".equals(discountType)) {
                        discountAmount = discountValue;
                    }

                    expectedDiscount = discountAmount;
                    finalAmount = orderAmount - discountAmount;
                }

                return AvailableUserCouponListResponse.AvailableUserCouponInfo.builder()
                    .id((Long) uc.get("id"))
                    .userId((Long) uc.get("userId"))
                    .couponId((Long) uc.get("couponId"))
                    .couponName((String) uc.get("couponName"))
                    .discountType((String) uc.get("discountType"))
                    .discountValue((Integer) uc.get("discountValue"))
                    .minOrderAmount((Integer) uc.get("minOrderAmount"))
                    .status((String) uc.get("status"))
                    .issuedAt((LocalDateTime) uc.get("issuedAt"))
                    .expiredAt((LocalDateTime) uc.get("expiredAt"))
                    .expectedDiscount(expectedDiscount)
                    .finalAmount(finalAmount)
                    .build();
            })
            .sorted((a, b) -> {
                // 할인 금액이 큰 순서로 정렬
                if (orderAmount != null && a.getExpectedDiscount() != null && b.getExpectedDiscount() != null) {
                    return Integer.compare(b.getExpectedDiscount(), a.getExpectedDiscount());
                }
                return b.getIssuedAt().compareTo(a.getIssuedAt());
            })
            .toList();

        return AvailableUserCouponListResponse.builder()
            .coupons(availableCoupons)
            .totalCount(availableCoupons.size())
            .orderAmount(orderAmount != null ? orderAmount : 0)
            .build();
    }
}
