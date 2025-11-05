package org.hhplus.hhecommerce.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.point.*;
import org.hhplus.hhecommerce.application.point.ChargePointUseCase;
import org.hhplus.hhecommerce.application.point.DeductPointUseCase;
import org.hhplus.hhecommerce.application.point.GetPointUseCase;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Point", description = "포인트 관리 API")
@RestController
@RequestMapping("/api/point")
@RequiredArgsConstructor
public class PointController {

    private final GetPointUseCase getPointUseCase;
    private final ChargePointUseCase chargePointUseCase;
    private final DeductPointUseCase deductPointUseCase;

    @Operation(summary = "포인트 조회")
    @GetMapping
    public PointResponse getPoint(
        @Parameter(description = "사용자 ID") @RequestParam(defaultValue = "1") Long userId
    ) {
        return getPointUseCase.execute(userId);
    }

    @Operation(summary = "포인트 충전")
    @PostMapping("/charge")
    public ChargeResponse chargePoint(
        @Parameter(description = "사용자 ID") @RequestParam(defaultValue = "1") Long userId,
        @RequestBody ChargeRequest request
    ) {
        return chargePointUseCase.execute(userId, request);
    }

    @Operation(summary = "포인트 차감")
    @PostMapping("/deduct")
    public DeductResponse deductPoint(
        @Parameter(description = "사용자 ID") @RequestParam(defaultValue = "1") Long userId,
        @RequestBody DeductRequest request
    ) {
        return deductPointUseCase.execute(userId, request);
    }
}
