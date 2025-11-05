package org.hhplus.hhecommerce.api.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Schema(description = "에러 응답")
public class ErrorResponse {

    @Schema(description = "에러 코드", example = "PRODUCT_NOT_FOUND")
    private String code;

    @Schema(description = "에러 메시지", example = "상품을 찾을 수 없습니다")
    private String message;

    @Schema(description = "발생 시각")
    private LocalDateTime timestamp;

    public ErrorResponse(String message) {
        this.code = null;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
