package org.hhplus.hhecommerce.api.dto.point;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "포인트 차감 요청")
public class DeductRequest {

    @Schema(description = "차감 금액 (원)", example = "150000", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer amount;
}
