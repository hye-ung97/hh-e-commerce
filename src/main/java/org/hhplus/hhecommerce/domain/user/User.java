package org.hhplus.hhecommerce.domain.user;

import jakarta.persistence.*;
import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;

@Getter
@Entity
@Table(name = "USER", indexes = {
    @Index(name = "idx_email", columnList = "email")
})
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    protected User() {
        super();
    }

    public User(Long id, String name, String email) {
        super();
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public User(String name, String email) {
        super();
        this.name = name;
        this.email = email;
    }

    public void setId(Long id) {
        this.id = id;
    }

}
