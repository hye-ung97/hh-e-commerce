package org.hhplus.hhecommerce.dto.balance;

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
@Schema(description = "잔액 거래 내역 응답")
public class BalanceHistoryResponse {

    @Schema(description = "거래 내역 목록")
    private List<BalanceTransaction> history;

    @Schema(description = "현재 잔액 (원)", example = "1000000")
    private Integer currentBalance;

    @Schema(description = "전체 거래 건수", example = "3")
    private Integer totalCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "잔액 거래 내역 상세")
    public static class BalanceTransaction {

        @Schema(description = "거래 ID", example = "1")
        private Long id;

        @Schema(description = "사용자 ID", example = "1")
        private Long userId;

        @Schema(description = "거래 유형", example = "CHARGE", allowableValues = {"CHARGE", "DEDUCT"})
        private String type;

        @Schema(description = "거래 금액 (원)", example = "1000000")
        private Integer amount;

        @Schema(description = "거래 후 잔액 (원)", example = "1000000")
        private Integer balanceAfter;

        @Schema(description = "거래 설명", example = "초기 충전")
        private String description;

        @Schema(description = "거래 일시", example = "2024-09-28T13:51:32.123")
        private LocalDateTime createdAt;
    }
}
