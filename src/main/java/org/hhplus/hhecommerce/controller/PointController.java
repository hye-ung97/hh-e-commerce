package org.hhplus.hhecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hhplus.hhecommerce.dto.point.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Tag(name = "Point", description = "포인트 관리 API")
@RestController
@RequestMapping("/api/point")
public class PointController {

    private static final Map<Long, Map<String, Object>> POINTS = new ConcurrentHashMap<>(
        Map.of(
            1L, createPoint(1L, 1L, 1000000),
            2L, createPoint(2L, 2L, 500000),
            3L, createPoint(3L, 3L, 2000000)
        )
    );

    private static Map<String, Object> createPoint(Long id, Long userId, int amount) {
        Map<String, Object> point = new HashMap<>();
        point.put("id", id);
        point.put("userId", userId);
        point.put("amount", amount);
        point.put("createdAt", LocalDateTime.now().minusDays(30));
        point.put("updatedAt", LocalDateTime.now().minusDays(1));
        return point;
    }

    @Operation(summary = "포인트 조회", description = "사용자의 현재 포인트를 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "포인트 조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = PointResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @GetMapping
    public PointResponse getPoint(
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId
    ) {
        Map<String, Object> point = POINTS.values().stream()
            .filter(p -> userId.equals(p.get("userId")))
            .findFirst()
            .orElse(null);

        if (point == null) {
            throw new RuntimeException("Point not found for user: " + userId);
        }

        return PointResponse.builder()
            .id((Long) point.get("id"))
            .userId((Long) point.get("userId"))
            .amount((Integer) point.get("amount"))
            .createdAt((LocalDateTime) point.get("createdAt"))
            .updatedAt((LocalDateTime) point.get("updatedAt"))
            .build();
    }

    @Operation(summary = "포인트 충전", description = "사용자의 포인트를 충전합니다.")
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
    public ChargeResponse chargePoint(
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

        // 기존 포인트 조회
        Map<String, Object> point = POINTS.values().stream()
            .filter(p -> userId.equals(p.get("userId")))
            .findFirst()
            .orElse(null);

        if (point == null) {
            Long newId = POINTS.keySet().stream()
                .max(Long::compareTo)
                .orElse(0L) + 1;

            point = new HashMap<>();
            point.put("id", newId);
            point.put("userId", userId);
            point.put("amount", amount);
            point.put("createdAt", LocalDateTime.now());
            point.put("updatedAt", LocalDateTime.now());

            POINTS.put(newId, point);
        } else {
            synchronized (point) {
                int currentAmount = (Integer) point.get("amount");
                int newAmount = currentAmount + amount;

                point.put("amount", newAmount);
                point.put("updatedAt", LocalDateTime.now());
            }
        }

        return ChargeResponse.builder()
            .id((Long) point.get("id"))
            .userId((Long) point.get("userId"))
            .amount((Integer) point.get("amount"))
            .createdAt((LocalDateTime) point.get("createdAt"))
            .updatedAt((LocalDateTime) point.get("updatedAt"))
            .chargedAmount(amount)
            .message("Point charged successfully")
            .build();
    }

    @Operation(summary = "포인트 차감", description = "사용자의 포인트를 차감합니다. (테스트용 엔드포인트)")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "차감 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DeductResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "포인트 부족 또는 잘못된 요청")
    })
    @PostMapping("/deduct")
    public DeductResponse deductPoint(
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

        Map<String, Object> point = POINTS.values().stream()
            .filter(p -> userId.equals(p.get("userId")))
            .findFirst()
            .orElse(null);

        if (point == null) {
            throw new RuntimeException("Point not found for user: " + userId);
        }

        synchronized (point) {
            int currentAmount = (Integer) point.get("amount");

            if (currentAmount < amount) {
                throw new RuntimeException("Insufficient point. Current: " + currentAmount + ", Required: " + amount);
            }

            int newAmount = currentAmount - amount;
            point.put("amount", newAmount);
            point.put("updatedAt", LocalDateTime.now());
        }

        return DeductResponse.builder()
            .id((Long) point.get("id"))
            .userId((Long) point.get("userId"))
            .amount((Integer) point.get("amount"))
            .createdAt((LocalDateTime) point.get("createdAt"))
            .updatedAt((LocalDateTime) point.get("updatedAt"))
            .deductedAmount(amount)
            .message("Point deducted successfully")
            .build();
    }

    @Operation(summary = "포인트 거래 내역 조회", description = "사용자의 포인트 거래 내역을 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = PointHistoryResponse.class)
            )
        )
    })
    @GetMapping("/history")
    public PointHistoryResponse getPointHistory(
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId
    ) {
        List<PointHistoryResponse.PointTransaction> history = List.of(
            new PointHistoryResponse.PointTransaction(
                1L,
                userId,
                "CHARGE",
                1000000,
                1000000,
                "초기 충전",
                LocalDateTime.now().minusDays(30)
            ),
            new PointHistoryResponse.PointTransaction(
                2L,
                userId,
                "DEDUCT",
                150000,
                850000,
                "주문 결제",
                LocalDateTime.now().minusDays(10)
            ),
            new PointHistoryResponse.PointTransaction(
                3L,
                userId,
                "CHARGE",
                200000,
                1050000,
                "추가 충전",
                LocalDateTime.now().minusDays(5)
            )
        );

        Map<String, Object> point = POINTS.values().stream()
            .filter(p -> userId.equals(p.get("userId")))
            .findFirst()
            .orElse(null);

        return PointHistoryResponse.builder()
            .history(history)
            .currentPoint(point != null ? (Integer) point.get("amount") : 0)
            .totalCount(history.size())
            .build();
    }
}
