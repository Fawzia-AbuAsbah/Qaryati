package com.qaryati.village.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "villages", schema = "public", uniqueConstraints = {
        @UniqueConstraint(name = "uq_village_governorate_name",
                columnNames = {"governorate_id", "name_ar"})
})
public class Village {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "governorate_id", nullable = false)
    private Governorate governorate;

    // V1 also enforces the same-governorate composite FK in PostgreSQL.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "local_authority_id", nullable = true)
    private LocalAuthority localAuthority;

    @Column(name = "name_ar", nullable = false, length = 180)
    private String nameAr;

    @Column(name = "name_en", length = 180)
    private String nameEn;

    @Column(name = "name_origin", columnDefinition = "text")
    private String nameOrigin;

    @Column(name = "location_description", nullable = false, columnDefinition = "text")
    private String locationDescription;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "elevation_m", precision = 9, scale = 2)
    private BigDecimal elevationM;

    @Column(name = "area_km2", precision = 12, scale = 3)
    private BigDecimal areaKm2;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VillageStatus status;

    // PostgreSQL TIMESTAMPTZ stores instants at microsecond precision.
    // Future service writes must account for Instant's nanosecond precision.
    // Status and timestamps are assigned by the service, without entity callbacks.
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    @Column(name = "archived_at", columnDefinition = "timestamptz")
    private Instant archivedAt;

    public Village() {
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

    public LocalAuthority getLocalAuthority() {
        return localAuthority;
    }

    public void setLocalAuthority(LocalAuthority localAuthority) {
        this.localAuthority = localAuthority;
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

    public String getNameOrigin() {
        return nameOrigin;
    }

    public void setNameOrigin(String nameOrigin) {
        this.nameOrigin = nameOrigin;
    }

    public String getLocationDescription() {
        return locationDescription;
    }

    public void setLocationDescription(String locationDescription) {
        this.locationDescription = locationDescription;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getElevationM() {
        return elevationM;
    }

    public void setElevationM(BigDecimal elevationM) {
        this.elevationM = elevationM;
    }

    public BigDecimal getAreaKm2() {
        return areaKm2;
    }

    public void setAreaKm2(BigDecimal areaKm2) {
        this.areaKm2 = areaKm2;
    }

    public VillageStatus getStatus() {
        return status;
    }

    public void setStatus(VillageStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

}