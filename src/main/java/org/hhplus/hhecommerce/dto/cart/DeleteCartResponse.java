package org.hhplus.hhecommerce.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "장바구니 삭제 응답")
public class DeleteCartResponse {

    @Schema(description = "삭제된 장바구니 ID", example = "1")
    private Long id;

    @Schema(description = "응답 메시지", example = "Cart item deleted successfully")
    private String message;
}
