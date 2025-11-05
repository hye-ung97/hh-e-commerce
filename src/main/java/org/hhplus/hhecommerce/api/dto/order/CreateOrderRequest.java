package org.hhplus.hhecommerce.api.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "주문 생성 요청 - 장바구니 기반 주문")
public class CreateOrderRequest {

    @Schema(description = "사용할 사용자 쿠폰 ID (선택사항)", example = "1")
    private Long userCouponId;

    // 참고: 현재는 장바구니에 담긴 모든 상품으로 주문을 생성합니다.
    // 향후 특정 상품만 주문하려면 아래와 같은 필드를 추가할 수 있습니다.
    // @Schema(description = "주문할 장바구니 항목 ID 목록 (선택사항, 비어있으면 전체 주문)")
    // private List<Long> cartItemIds;
}
