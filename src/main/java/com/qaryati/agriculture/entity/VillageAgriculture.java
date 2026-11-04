
package com.qaryati.agriculture.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "village_agriculture",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"village_id", "product_id"}
        )
)
public class VillageAgriculture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "village_id", nullable = false)
    private Long villageId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "importance", length = 16)
    private AgricultureImportance importance;

    protected VillageAgriculture() {
    }

    public VillageAgriculture(
            Long villageId,
            Long productId,
            String description,
            AgricultureImportance importance) {
        this.villageId = villageId;
        this.productId = productId;
        this.description = description;
        this.importance = importance;
    }

    public Long getId() {
        return id;
    }

    public Long getVillageId() {
        return villageId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getDescription() {
        return description;
    }

    public AgricultureImportance getImportance() {
        return importance;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setImportance(AgricultureImportance importance) {
        this.importance = importance;
    }
}
