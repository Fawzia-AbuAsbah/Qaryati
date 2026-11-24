package com.qaryati.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "economic_activities", schema = "public", uniqueConstraints = @UniqueConstraint(
        columnNames = {"village_id", "name", "activity_type"}))
public class EconomicActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "village_id", nullable = false)
    private Long villageId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 28)
    private EconomicActivityType activityType;

    @Column(columnDefinition = "TEXT")
    private String description;

    protected EconomicActivity() {
    }

    public EconomicActivity(Long villageId, String name, EconomicActivityType activityType,
                            String description) {
        this.villageId = villageId;
        this.name = name;
        this.activityType = activityType;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Long getVillageId() {
        return villageId;
    }

    public String getName() {
        return name;
    }

    public EconomicActivityType getActivityType() {
        return activityType;
    }

    public String getDescription() {
        return description;
    }

    public void setVillageId(Long villageId) {
        this.villageId = villageId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setActivityType(EconomicActivityType activityType) {
        this.activityType = activityType;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
