package ru.itis.documents.domain.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "care_profiles")
public class CareProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "species_id", nullable = false, unique = true)
    @ToString.Exclude
    private PlantSpecies species;

    @Column(name = "water_interval_days")
    private Integer waterIntervalDays;

    @Column(name = "light_level", length = 30)
    private String lightLevel;

    @Column(name = "humidity_percent")
    private Integer humidityPercent;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}