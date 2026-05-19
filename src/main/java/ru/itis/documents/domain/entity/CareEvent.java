package ru.itis.documents.domain.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import ru.itis.documents.domain.enums.CareActionType;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "care_events")
public class CareEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_plant_id", nullable = false)
    @ToString.Exclude
    private UserPlant userPlant;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    @ToString.Include
    private CareActionType type;

    @Column(name = "event_time", nullable = false)
    private OffsetDateTime eventTime;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @PrePersist
    public void prePersist() {
        if (this.eventTime == null) {
            this.eventTime = OffsetDateTime.now();
        }
    }
}