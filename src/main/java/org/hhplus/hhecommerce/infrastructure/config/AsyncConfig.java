package org.hhplus.hhecommerce.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final AsyncProperties asyncProperties;

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncProperties.getCorePoolSize());
        executor.setMaxPoolSize(asyncProperties.getMaxPoolSize());
        executor.setQueueCapacity(asyncProperties.getQueueCapacity());
        executor.setThreadNamePrefix(asyncProperties.getThreadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(asyncProperties.isWaitForTasksToCompleteOnShutdown());
        executor.setAwaitTerminationSeconds(asyncProperties.getAwaitTerminationSeconds());
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("비동기 작업 거부됨 - 호출 스레드에서 직접 실행합니다. " +
                    "activeCount={}, poolSize={}/{}, queueSize={}/{}",
                    e.getActiveCount(),
                    e.getPoolSize(),
                    e.getMaximumPoolSize(),
                    e.getQueue().size(),
                    asyncProperties.getQueueCapacity());

            if (!e.isShutdown()) {
                r.run();
            } else {
                log.error("Executor가 shutdown 상태 - 작업을 실행할 수 없습니다. task={}",
                        r.getClass().getSimpleName());
            }
        });
        executor.initialize();

        log.info("비동기 스레드 풀 초기화 완료 - corePoolSize={}, maxPoolSize={}, queueCapacity={}, threadNamePrefix={}",
                asyncProperties.getCorePoolSize(),
                asyncProperties.getMaxPoolSize(),
                asyncProperties.getQueueCapacity(),
                asyncProperties.getThreadNamePrefix());

        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new OrderAsyncExceptionHandler();
    }
}
