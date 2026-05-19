package ru.itis.documents.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "identification_candidates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"identification"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class IdentificationCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "identification_id", nullable = false)
    private PlantIdentification identification;

    @Column(name = "scientific_name", nullable = false)
    private String scientificName;

    @Column(name = "common_names", length = 1000)
    private String commonNames;

    @Column(name = "score", nullable = false)
    private Double score;
}