package org.hhplus.hhecommerce.domain.user;

import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;

@Getter
public class User extends BaseTimeEntity {

    private Long id;
    private String name;
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
