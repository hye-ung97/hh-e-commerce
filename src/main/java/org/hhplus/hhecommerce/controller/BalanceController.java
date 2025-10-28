package org.hhplus.hhecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hhplus.hhecommerce.dto.balance.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Tag(name = "Balance", description = "잔액 관리 API")
@RestController
@RequestMapping("/api/balance")
public class BalanceController {

    private static final Map<Long, Map<String, Object>> BALANCES = new ConcurrentHashMap<>(
        Map.of(
            1L, createBalance(1L, 1L, 1000000),
            2L, createBalance(2L, 2L, 500000),
            3L, createBalance(3L, 3L, 2000000)
        )
    );

    private static Map<String, Object> createBalance(Long id, Long userId, int amount) {
        Map<String, Object> balance = new HashMap<>();
        balance.put("id", id);
        balance.put("userId", userId);
        balance.put("amount", amount);
        balance.put("createdAt", LocalDateTime.now().minusDays(30));
        balance.put("updatedAt", LocalDateTime.now().minusDays(1));
        return balance;
    }

    @Operation(summary = "잔액 조회", description = "사용자의 현재 잔액을 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "잔액 조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = BalanceResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @GetMapping
    public BalanceResponse getBalance(
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId
    ) {
        Map<String, Object> balance = BALANCES.values().stream()
            .filter(b -> userId.equals(b.get("userId")))
            .findFirst()
            .orElse(null);

        if (balance == null) {
            throw new RuntimeException("Balance not found for user: " + userId);
        }

        return BalanceResponse.builder()
            .id((Long) balance.get("id"))
            .userId((Long) balance.get("userId"))
            .amount((Integer) balance.get("amount"))
            .createdAt((LocalDateTime) balance.get("createdAt"))
            .updatedAt((LocalDateTime) balance.get("updatedAt"))
            .build();
    }

    @Operation(summary = "잔액 충전", description = "사용자의 잔액을 충전합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "충전 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ChargeResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 (충전 금액이 0 이하이거나 1천만원 초과)")
    })
    @PostMapping("/charge")
    public ChargeResponse chargeBalance(
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "충전 요청",
            content = @Content(
                schema = @Schema(implementation = ChargeRequest.class)
            )
        )
        @RequestBody ChargeRequest request
    ) {
        int amount = request.getAmount();

        if (amount <= 0) {
            throw new RuntimeException("Charge amount must be greater than 0");
        }

        if (amount > 10000000) {
            throw new RuntimeException("Maximum charge amount is 10,000,000 won");
        }

        // 기존 잔액 조회
        Map<String, Object> balance = BALANCES.values().stream()
            .filter(b -> userId.equals(b.get("userId")))
            .findFirst()
            .orElse(null);

        if (balance == null) {
            Long newId = BALANCES.keySet().stream()
                .max(Long::compareTo)
                .orElse(0L) + 1;

            balance = new HashMap<>();
            balance.put("id", newId);
            balance.put("userId", userId);
            balance.put("amount", amount);
            balance.put("createdAt", LocalDateTime.now());
            balance.put("updatedAt", LocalDateTime.now());

            BALANCES.put(newId, balance);
        } else {
            synchronized (balance) {
                int currentAmount = (Integer) balance.get("amount");
                int newAmount = currentAmount + amount;

                balance.put("amount", newAmount);
                balance.put("updatedAt", LocalDateTime.now());
            }
        }

        return ChargeResponse.builder()
            .id((Long) balance.get("id"))
            .userId((Long) balance.get("userId"))
            .amount((Integer) balance.get("amount"))
            .createdAt((LocalDateTime) balance.get("createdAt"))
            .updatedAt((LocalDateTime) balance.get("updatedAt"))
            .chargedAmount(amount)
            .message("Balance charged successfully")
            .build();
    }

    @Operation(summary = "잔액 차감", description = "사용자의 잔액을 차감합니다. (테스트용 엔드포인트)")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "차감 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DeductResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "잔액 부족 또는 잘못된 요청")
    })
    @PostMapping("/deduct")
    public DeductResponse deductBalance(
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "차감 요청",
            content = @Content(
                schema = @Schema(implementation = DeductRequest.class)
            )
        )
        @RequestBody DeductRequest request
    ) {
        int amount = request.getAmount();

        if (amount <= 0) {
            throw new RuntimeException("Deduct amount must be greater than 0");
        }

        Map<String, Object> balance = BALANCES.values().stream()
            .filter(b -> userId.equals(b.get("userId")))
            .findFirst()
            .orElse(null);

        if (balance == null) {
            throw new RuntimeException("Balance not found for user: " + userId);
        }

        synchronized (balance) {
            int currentAmount = (Integer) balance.get("amount");

            if (currentAmount < amount) {
                throw new RuntimeException("Insufficient balance. Current: " + currentAmount + ", Required: " + amount);
            }

            int newAmount = currentAmount - amount;
            balance.put("amount", newAmount);
            balance.put("updatedAt", LocalDateTime.now());
        }

        return DeductResponse.builder()
            .id((Long) balance.get("id"))
            .userId((Long) balance.get("userId"))
            .amount((Integer) balance.get("amount"))
            .createdAt((LocalDateTime) balance.get("createdAt"))
            .updatedAt((LocalDateTime) balance.get("updatedAt"))
            .deductedAmount(amount)
            .message("Balance deducted successfully")
            .build();
    }

    @Operation(summary = "잔액 거래 내역 조회", description = "사용자의 잔액 거래 내역을 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = BalanceHistoryResponse.class)
            )
        )
    })
    @GetMapping("/history")
    public BalanceHistoryResponse getBalanceHistory(
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId
    ) {
        List<BalanceHistoryResponse.BalanceTransaction> history = List.of(
            new BalanceHistoryResponse.BalanceTransaction(
                1L,
                userId,
                "CHARGE",
                1000000,
                1000000,
                "초기 충전",
                LocalDateTime.now().minusDays(30)
            ),
            new BalanceHistoryResponse.BalanceTransaction(
                2L,
                userId,
                "DEDUCT",
                150000,
                850000,
                "주문 결제",
                LocalDateTime.now().minusDays(10)
            ),
            new BalanceHistoryResponse.BalanceTransaction(
                3L,
                userId,
                "CHARGE",
                200000,
                1050000,
                "추가 충전",
                LocalDateTime.now().minusDays(5)
            )
        );

        Map<String, Object> balance = BALANCES.values().stream()
            .filter(b -> userId.equals(b.get("userId")))
            .findFirst()
            .orElse(null);

        return BalanceHistoryResponse.builder()
            .history(history)
            .currentBalance(balance != null ? (Integer) balance.get("amount") : 0)
            .totalCount(history.size())
            .build();
    }
}
