
package com.qaryati.agriculture.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "agricultural_products")
public class AgriculturalProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_ar", nullable = false,
            unique = true, length = 160)
    private String nameAr;

    @Column(name = "name_en", length = 160)
    private String nameEn;

    @Column(name = "product_type", nullable = false,
            length = 25)
    private String productType;

    public AgriculturalProduct() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNameAr() {
        return nameAr;
    }

    public void setNameAr(String nameAr) {
        this.nameAr = nameAr;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }
}
