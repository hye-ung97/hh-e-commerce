package org.hhplus.hhecommerce.application.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.common.DomainEventPublisher;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserCreatedEvent;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public User createUser(String name, String email) {
        User user = userRepository.save(new User(name, email));
        eventPublisher.publish(UserCreatedEvent.of(user.getId()));
        log.info("사용자 생성 완료 - userId: {}, email: {}", user.getId(), email);
        return user;
    }

    @Transactional
    public User createUser(String name, String email, String phone) {
        User user = userRepository.save(new User(name, email, phone));
        eventPublisher.publish(UserCreatedEvent.of(user.getId()));
        log.info("사용자 생성 완료 - userId: {}, email: {}", user.getId(), email);
        return user;
    }
}
