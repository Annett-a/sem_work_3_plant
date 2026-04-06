package ru.itis.documents.domain.entity;

import jakarta.persistence.*;
import lombok.*;

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

    // O2O: species_id UNIQUE в БД
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