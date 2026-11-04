package com.qaryati.village.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "village_names", schema = "public", uniqueConstraints =
        @UniqueConstraint(columnNames = {"village_id", "name", "language_code", "name_type"}))
public class VillageName {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "village_id", nullable = false)
    private Village village;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "language_code", nullable = false, length = 12)
    private String languageCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "name_type", nullable = false, length = 24)
    private VillageNameType nameType;
    @Column(columnDefinition = "text")
    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Village getVillage() { return village; }
    public void setVillage(Village village) { this.village = village; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }
    public VillageNameType getNameType() { return nameType; }
    public void setNameType(VillageNameType nameType) { this.nameType = nameType; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}