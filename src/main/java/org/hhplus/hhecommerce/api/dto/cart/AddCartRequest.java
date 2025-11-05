package org.hhplus.hhecommerce.api.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "장바구니 상품 추가 요청")
public class AddCartRequest {

    @Schema(description = "상품 옵션 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long productOptionId;

    @Schema(description = "수량", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;
}
