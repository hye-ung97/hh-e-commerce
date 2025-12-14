package org.hhplus.hhecommerce.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                log.error("비동기 작업 거부됨 - 스레드풀 포화 상태. " +
                        "activeCount={}, poolSize={}/{}, queueSize={}/{}. " +
                        "이벤트 리스너에서 DLQ에 저장됩니다.",
                        e.getActiveCount(),
                        e.getPoolSize(),
                        e.getMaximumPoolSize(),
                        e.getQueue().size(),
                        asyncProperties.getQueueCapacity());
                throw new RejectedExecutionException("Async task rejected - thread pool saturated");
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
