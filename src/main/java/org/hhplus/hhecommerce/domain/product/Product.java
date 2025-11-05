package org.hhplus.hhecommerce.domain.product;

import lombok.Getter;
import lombok.Setter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;

@Getter
public class Product extends BaseTimeEntity {

    @Setter
    private Long id;
    private String name;
    private String description;
    private String category;
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
}
