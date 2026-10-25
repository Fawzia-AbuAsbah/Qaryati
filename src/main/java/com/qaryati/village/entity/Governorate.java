
package com.qaryati.village.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "governorates", schema = "public")
public class Governorate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_ar", nullable = false,
            unique = true, length = 120)
    private String nameAr;

    @Column(name = "name_en", length = 120)
    private String nameEn;

    @Column(name = "code", unique = true, length = 32)
    private String code;

    public Governorate() {
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
