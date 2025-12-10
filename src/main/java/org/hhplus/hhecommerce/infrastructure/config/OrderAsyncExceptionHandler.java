package org.hhplus.hhecommerce.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;
import java.util.Arrays;

@Slf4j
public class OrderAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error("[비동기 작업 실패] method={}, params={}, exception={}",
                method.getName(),
                Arrays.toString(params),
                ex.getMessage(),
                ex);
    }
}
