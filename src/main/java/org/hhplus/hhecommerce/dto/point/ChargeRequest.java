package org.hhplus.hhecommerce.dto.point;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "포인트 충전 요청")
public class ChargeRequest {

    @Schema(description = "충전 금액 (원)", example = "500000", required = true)
    private Integer amount;
}
