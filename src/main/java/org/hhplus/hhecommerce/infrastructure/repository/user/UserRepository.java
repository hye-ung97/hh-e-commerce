package org.hhplus.hhecommerce.infrastructure.repository.user;

import org.hhplus.hhecommerce.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
