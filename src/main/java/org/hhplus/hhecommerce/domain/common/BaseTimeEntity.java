package org.hhplus.hhecommerce.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    protected LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    protected LocalDateTime updatedAt;

    protected BaseTimeEntity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    protected void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
