package org.hhplus.hhecommerce.domain.user;

public record UserCreatedEvent(Long userId) {

    public static UserCreatedEvent of(Long userId) {
        return new UserCreatedEvent(userId);
    }
}
