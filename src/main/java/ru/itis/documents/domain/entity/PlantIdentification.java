package ru.itis.documents.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.itis.documents.domain.enums.PlantIdentificationStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plant_identifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "candidates"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PlantIdentification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PlantIdentificationStatus status;

    @Column(name = "source_photo_path", nullable = false, length = 512)
    private String sourcePhotoPath;

    @Column(name = "photo_hash", length = 64)
    private String photoHash;

    @Column(name = "selected_scientific_name")
    private String selectedScientificName;

    @Column(name = "best_match")
    private String bestMatch;

    @Column(name = "best_match_score")
    private Double bestMatchScore;

    @Column(name = "plantnet_remaining_requests")
    private Integer plantnetRemainingRequests;

    @Column(name = "raw_response_json", columnDefinition = "text")
    private String rawResponseJson;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Builder.Default
    @OneToMany(mappedBy = "identification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IdentificationCandidate> candidates = new ArrayList<>();

    public void addCandidate(IdentificationCandidate c) {
        candidates.add(c);
        c.setIdentification(this);
    }

    public void clearCandidates() {
        for (IdentificationCandidate c : candidates) {
            c.setIdentification(null);
        }
        candidates.clear();
    }
}