package org.hhplus.hhecommerce.domain.user;

import jakarta.persistence.*;
import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;

import java.util.regex.Pattern;

@Getter
@Entity
@Table(name = "USER", indexes = {
    @Index(name = "idx_email", columnList = "email")
})
public class User extends BaseTimeEntity {

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^01[0-9][- ]?\\d{3,4}[- ]?\\d{4}$"
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(length = 20)
    private String phone;

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

    public User(String name, String email, String phone) {
        super();
        this.name = name;
        this.email = email;
        this.phone = normalizePhone(phone);
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setPhone(String phone) {
        this.phone = normalizePhone(phone);
    }

    public boolean hasValidPhone() {
        return phone != null && !phone.isBlank();
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        String trimmed = phone.trim();
        if (!PHONE_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("올바르지 않은 전화번호 형식입니다: " + phone);
        }

        return trimmed.replaceAll("[- ]", "");
    }
}
