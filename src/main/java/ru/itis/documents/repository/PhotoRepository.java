package ru.itis.documents.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.documents.domain.entity.Photo;

import java.util.List;
import java.util.Optional;

/**
 * Этап 9.1 (P0): стандартный Spring Data репозиторий.
 * CRUD обеспечивается JpaRepository.
 */
public interface PhotoRepository extends JpaRepository<Photo, Long> {

    List<Photo> findAllByUserPlant_IdOrderByUploadedAtDesc(Long userPlantId);
    Optional<Photo> findByIdAndUserPlant_User_Id(Long id, Long userId);
    Optional<Photo> findByIdAndUserPlant_IdAndUserPlant_User_Id(Long id, Long userPlantId, Long userId);
}
