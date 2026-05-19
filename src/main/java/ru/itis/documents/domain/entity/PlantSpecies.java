package ru.itis.documents.domain.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "plant_species")
public class PlantSpecies {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    @ToString.Include
    private String name;

    @Column(name = "latin_name", length = 200)
    private String latinName;

    @Column(name = "external_id")
    private Long externalId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToOne(mappedBy = "species", fetch = FetchType.LAZY)
    @ToString.Exclude
    private CareProfile careProfile;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "plant_species_tags",
            joinColumns = @JoinColumn(name = "species_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @ToString.Exclude
    private Set<Tag> tags = new HashSet<>();

    @OneToMany(mappedBy = "species", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<UserPlant> userPlants = new HashSet<>();

    @PrePersist
    public void prePersist() {
        this.createdAt = OffsetDateTime.now();

    }

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "image_lookup_attempted", nullable = false)
    private boolean imageLookupAttempted;
}