package org.hhplus.hhecommerce.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "주문 생성 요청")
public class CreateOrderRequest {

    @Schema(description = "주문 항목 목록", required = true)
    private List<OrderItem> items;

    @Schema(description = "사용할 쿠폰 ID (선택사항)", example = "1")
    private Long userCouponId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "주문 항목")
    public static class OrderItem {

        @Schema(description = "상품 옵션 ID", example = "1", required = true)
        private Long productOptionId;

        @Schema(description = "수량", example = "1", required = true)
        private Integer quantity;
    }
}
