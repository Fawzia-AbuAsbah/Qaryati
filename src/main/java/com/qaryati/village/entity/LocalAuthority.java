package com.qaryati.village.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "local_authorities", schema = "public", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"governorate_id", "name_ar"}),
        @UniqueConstraint(name = "uq_local_authority_governorate", columnNames = {"id", "governorate_id"})
})
public class LocalAuthority {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "governorate_id", nullable = false)
    private Governorate governorate;

    @Column(name = "name_ar", nullable = false, length = 180)
    private String nameAr;

    @Column(name = "name_en", length = 180)
    private String nameEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "authority_type", nullable = false, length = 32)
    private LocalAuthorityType authorityType;

    public LocalAuthority() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Governorate getGovernorate() {
        return governorate;
    }

    public void setGovernorate(Governorate governorate) {
        this.governorate = governorate;
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

    public LocalAuthorityType getAuthorityType() {
        return authorityType;
    }

    public void setAuthorityType(LocalAuthorityType authorityType) {
        this.authorityType = authorityType;
    }
}
