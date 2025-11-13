package org.hhplus.hhecommerce.domain.product;

import jakarta.persistence.*;
import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;

@Getter
@Entity
@Table(name = "PRODUCT", indexes = {
    @Index(name = "idx_category", columnList = "category"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    protected Product() {
        super();
    }

    public Product(Long id, String name, String description, String category, ProductStatus status) {
        super();
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.status = status;
    }

    public Product(String name, String description, String category) {
        super();
        this.name = name;
        this.description = description;
        this.category = category;
        this.status = ProductStatus.ACTIVE;
    }

    public void activate() {
        this.status = ProductStatus.ACTIVE;
        updateTimestamp();
    }

    public void deactivate() {
        this.status = ProductStatus.INACTIVE;
        updateTimestamp();
    }


    public boolean isActive() {
        return this.status == ProductStatus.ACTIVE;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
