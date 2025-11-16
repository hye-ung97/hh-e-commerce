package org.hhplus.hhecommerce.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hhplus.hhecommerce.api.dto.point.ChargeRequest;
import org.hhplus.hhecommerce.api.dto.point.DeductRequest;
import org.hhplus.hhecommerce.config.TestContainersConfig;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional
@DisplayName("PointController 통합 테스트")
class PointControllerIntegrationTest extends TestContainersConfig {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Point testPoint;

    @BeforeEach
    void setUp() {
        pointRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User("테스트유저", "point-test@example.com");
        testUser = userRepository.save(testUser);

        testPoint = new Point(testUser.getId());
        testPoint.charge(10000);
        testPoint = pointRepository.save(testPoint);
    }

    @Test
    @DisplayName("포인트 조회 - 성공")
    void getPoint_success() throws Exception {
        // when & then
        mockMvc.perform(get("/api/point")
                        .param("userId", testUser.getId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(testUser.getId().intValue())))
                .andExpect(jsonPath("$.amount", is(10000)));
    }

    @Test
    @DisplayName("포인트 조회 - 포인트가 없는 새 사용자 (실패)")
    void getPoint_newUser() throws Exception {
        // given
        User newUser = new User("신규유저", "newuser@example.com");
        newUser = userRepository.save(newUser);

        // when & then - 포인트가 없으면 POINT_NOT_FOUND 예외 발생
        mockMvc.perform(get("/api/point")
                        .param("userId", newUser.getId().toString()))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("포인트 충전 - 성공")
    void chargePoint_success() throws Exception {
        // given
        ChargeRequest request = new ChargeRequest(5000);

        // when & then
        mockMvc.perform(post("/api/point/charge")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(testUser.getId().intValue())))
                .andExpect(jsonPath("$.amount", is(15000))) // 10000 + 5000
                .andExpect(jsonPath("$.chargedAmount", is(5000)));
    }

    @Test
    @DisplayName("포인트 충전 - 최대 잔액 초과 (실패)")
    void chargePoint_exceedMaxBalance() throws Exception {
        // given
        ChargeRequest request = new ChargeRequest(95000); // 현재 10000 + 95000 = 105000 > 100000

        // when & then
        mockMvc.perform(post("/api/point/charge")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("포인트 충전 - 100원 단위가 아닌 경우 (실패)")
    void chargePoint_invalidUnit() throws Exception {
        // given
        ChargeRequest request = new ChargeRequest(5050); // 100원 단위가 아님

        // when & then
        mockMvc.perform(post("/api/point/charge")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("포인트 충전 - 0 이하 금액 (실패)")
    void chargePoint_invalidAmount() throws Exception {
        // given
        ChargeRequest request = new ChargeRequest(0);

        // when & then
        mockMvc.perform(post("/api/point/charge")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("포인트 충전 - 음수 금액 (실패)")
    void chargePoint_negativeAmount() throws Exception {
        // given
        ChargeRequest request = new ChargeRequest(-1000);

        // when & then
        mockMvc.perform(post("/api/point/charge")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("포인트 차감 - 성공")
    void deductPoint_success() throws Exception {
        // given
        DeductRequest request = new DeductRequest(3000);

        // when & then
        mockMvc.perform(post("/api/point/deduct")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(testUser.getId().intValue())))
                .andExpect(jsonPath("$.amount", is(7000))) // 10000 - 3000
                .andExpect(jsonPath("$.deductedAmount", is(3000)));
    }

    @Test
    @DisplayName("포인트 차감 - 잔액 부족 (실패)")
    void deductPoint_insufficientBalance() throws Exception {
        // given
        DeductRequest request = new DeductRequest(15000); // 현재 잔액 10000보다 큼

        // when & then
        mockMvc.perform(post("/api/point/deduct")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("포인트 차감 - 최소 사용 금액 미만 (실패)")
    void deductPoint_belowMinAmount() throws Exception {
        // given
        DeductRequest request = new DeductRequest(500); // 최소 사용 금액 1000원 미만

        // when & then
        mockMvc.perform(post("/api/point/deduct")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("포인트 차감 - 100원 단위가 아닌 경우 (실패)")
    void deductPoint_invalidUnit() throws Exception {
        // given
        DeductRequest request = new DeductRequest(1550); // 100원 단위가 아님

        // when & then
        mockMvc.perform(post("/api/point/deduct")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("포인트 차감 - 0 이하 금액 (실패)")
    void deductPoint_invalidAmount() throws Exception {
        // given
        DeductRequest request = new DeductRequest(0);

        // when & then
        mockMvc.perform(post("/api/point/deduct")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("포인트 충전 후 차감 시나리오")
    void chargeAndDeduct_scenario() throws Exception {
        // given - 먼저 포인트 충전
        ChargeRequest chargeRequest = new ChargeRequest(20000);

        mockMvc.perform(post("/api/point/charge")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chargeRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(30000))); // 10000 + 20000

        // when & then - 포인트 차감
        DeductRequest deductRequest = new DeductRequest(5000);

        mockMvc.perform(post("/api/point/deduct")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deductRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(25000))); // 30000 - 5000

        mockMvc.perform(get("/api/point")
                        .param("userId", testUser.getId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(25000)));
    }

    @Test
    @DisplayName("존재하지 않는 사용자 - 포인트 조회 (실패)")
    void getPoint_userNotFound() throws Exception {
        // given
        Long nonExistentUserId = 88888L;

        // when & then - 포인트가 없으면 POINT_NOT_FOUND 예외 발생
        mockMvc.perform(get("/api/point")
                        .param("userId", nonExistentUserId.toString()))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("존재하지 않는 사용자 - 포인트 충전 (실패)")
    void chargePoint_userNotFound() throws Exception {
        // given
        Long nonExistentUserId = 99999L;
        ChargeRequest request = new ChargeRequest(5000);

        // when & then - 사용자가 없으면 USER_NOT_FOUND 예외 발생
        mockMvc.perform(post("/api/point/charge")
                        .param("userId", nonExistentUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("최대 잔액까지 충전")
    void chargePoint_toMaxBalance() throws Exception {
        // given - 현재 잔액이 10000원인 상태에서 90000원 충전 (총 100000원)
        ChargeRequest request = new ChargeRequest(90000);

        // when & then
        mockMvc.perform(post("/api/point/charge")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(100000))); // 정확히 최대 잔액
    }

    @Test
    @DisplayName("전액 차감")
    void deductPoint_fullAmount() throws Exception {
        // given - 전체 잔액 차감 (10000원 전액)
        DeductRequest request = new DeductRequest(10000);

        // when & then
        mockMvc.perform(post("/api/point/deduct")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(0))); // 잔액 0
    }
}
