package org.hhplus.hhecommerce.api.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "주문 상세 응답")
public class OrderDetailResponse {

    @Schema(description = "주문 ID", example = "1")
    private Long id;

    @Schema(description = "사용자 ID", example = "1")
    private Long userId;

    @Schema(description = "주문 상태", example = "COMPLETED")
    private String status;

    @Schema(description = "총 상품 금액 (원)", example = "1500000")
    private Integer totalAmount;

    @Schema(description = "쿠폰 할인 금액 (원)", example = "150000")
    private Integer discountAmount;

    @Schema(description = "최종 결제 금액 (원)", example = "1350000")
    private Integer finalAmount;

    @Schema(description = "사용한 쿠폰명", example = "신규가입 10% 할인")
    private String couponName;

    @Schema(description = "주문 항목 목록")
    private List<OrderItemDetail> items;

    @Schema(description = "생성 일시", example = "2025-10-28T10:00:00")
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "주문 항목 상세")
    public static class OrderItemDetail {

        @Schema(description = "상품명", example = "노트북")
        private String productName;

        @Schema(description = "옵션명", example = "색상: 실버")
        private String optionName;

        @Schema(description = "가격 (원)", example = "1500000")
        private Integer price;

        @Schema(description = "수량", example = "1")
        private Integer quantity;

        @Schema(description = "합계 (원)", example = "1500000")
        private Integer totalPrice;
    }
}
