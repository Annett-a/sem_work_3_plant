package ru.itis.documents.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itis.documents.domain.entity.PlantIdentification;
import ru.itis.documents.domain.enums.PlantIdentificationStatus;

import java.util.List;
import java.util.Optional;

public interface PlantIdentificationRepository extends JpaRepository<PlantIdentification, Long> {

    @Query("""
                select pi
                from PlantIdentification pi
                where pi.user.id = :userId
                order by pi.createdAt desc
            """)
    List<PlantIdentification> findHistory(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"candidates"})
    Optional<PlantIdentification> findByIdAndUser_Id(Long id, Long userId);

    @EntityGraph(attributePaths = {"candidates"})
    Optional<PlantIdentification> findFirstByPhotoHashAndStatusOrderByCreatedAtDesc(
            String photoHash,
            PlantIdentificationStatus status
    );

    @EntityGraph(attributePaths = {"candidates"})
    Optional<PlantIdentification> findFirstByUser_IdAndPhotoHashAndStatusOrderByCreatedAtDesc(
            Long userId,
            String photoHash,
            PlantIdentificationStatus status
    );
}