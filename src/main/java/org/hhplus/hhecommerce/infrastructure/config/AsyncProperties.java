package org.hhplus.hhecommerce.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "async.thread-pool")
public class AsyncProperties {

    @Min(value = 1, message = "corePoolSize는 1 이상이어야 합니다")
    private int corePoolSize = 5;

    @Min(value = 1, message = "maxPoolSize는 1 이상이어야 합니다")
    private int maxPoolSize = 10;

    @Min(value = 0, message = "queueCapacity는 0 이상이어야 합니다")
    private int queueCapacity = 100;

    @NotBlank(message = "threadNamePrefix는 비어있을 수 없습니다")
    private String threadNamePrefix = "async-order-";

    @Min(value = 0, message = "awaitTerminationSeconds는 0 이상이어야 합니다")
    private int awaitTerminationSeconds = 30;

    private boolean waitForTasksToCompleteOnShutdown = true;
}
