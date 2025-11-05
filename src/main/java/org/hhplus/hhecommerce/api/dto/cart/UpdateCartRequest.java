package org.hhplus.hhecommerce.api.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "장바구니 수량 변경 요청")
public class UpdateCartRequest {

    @Schema(description = "변경할 수량", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;
}
