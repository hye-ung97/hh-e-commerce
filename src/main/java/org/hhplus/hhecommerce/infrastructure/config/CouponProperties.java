package org.hhplus.hhecommerce.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "coupon.pending")
public class CouponProperties {

    private long timeoutMs = 30000L;

    private long cleanupTimeoutMs = 60000L;

    private long cleanupIntervalMs = 60000L;
}
