package org.hhplus.hhecommerce.api.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장바구니 삭제 응답")
public record DeleteCartResponse(
        @Schema(description = "삭제된 장바구니 ID", example = "1")
        Long id,

        @Schema(description = "응답 메시지", example = "Cart item deleted successfully")
        String message
) {
}
