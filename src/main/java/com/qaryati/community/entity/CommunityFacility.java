package com.qaryati.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "community_facilities", schema = "public")
public class CommunityFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "village_id", nullable = false)
    private Long villageId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "facility_type", nullable = false, length = 26)
    private CommunityFacilityType facilityType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    protected CommunityFacility() {
    }

    public CommunityFacility(Long villageId, String name, CommunityFacilityType facilityType,
                             String description, BigDecimal latitude, BigDecimal longitude) {
        this.villageId = villageId;
        this.name = name;
        this.facilityType = facilityType;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() { return id; }
    public Long getVillageId() { return villageId; }
    public String getName() { return name; }
    public CommunityFacilityType getFacilityType() { return facilityType; }
    public String getDescription() { return description; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }

    public void setVillageId(Long villageId) { this.villageId = villageId; }
    public void setName(String name) { this.name = name; }
    public void setFacilityType(CommunityFacilityType facilityType) { this.facilityType = facilityType; }
    public void setDescription(String description) { this.description = description; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
}
