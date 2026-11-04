package com.qaryati.village.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "village_relations", schema = "public")
public class VillageRelation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "village_id", nullable = false)
    private Village village;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_village_id")
    private Village relatedVillage;
    @Column(name = "related_place_name", length = 200)
    private String relatedPlaceName;
    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 28)
    private VillageRelationType relationType;
    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Village getVillage() { return village; }
    public void setVillage(Village village) { this.village = village; }
    public Village getRelatedVillage() { return relatedVillage; }
    public void setRelatedVillage(Village village) { relatedVillage = village; }
    public String getRelatedPlaceName() { return relatedPlaceName; }
    public void setRelatedPlaceName(String name) { relatedPlaceName = name; }
    public VillageRelationType getRelationType() { return relationType; }
    public void setRelationType(VillageRelationType type) { relationType = type; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distance) { distanceKm = distance; }
}
