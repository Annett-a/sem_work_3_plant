package ru.itis.documents.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.documents.domain.entity.CareEvent;

import java.util.List;
import java.util.Optional;

public interface CareEventRepository extends JpaRepository<CareEvent, Long> {

    @EntityGraph(attributePaths = {"userPlant"})
    List<CareEvent> findAllByUserPlant_IdOrderByEventTimeDesc(Long userPlantId);

    @EntityGraph(attributePaths = {"userPlant"})
    List<CareEvent> findTop10ByUserPlant_IdOrderByEventTimeDesc(Long userPlantId);

    /** Этап 8 (P0): REST CRUD — выборка событий текущего пользователя */
    @EntityGraph(attributePaths = {"userPlant"})
    List<CareEvent> findAllByUserPlant_User_IdOrderByEventTimeDesc(Long userId);

    /** Этап 8 (P0): REST CRUD — выборка по растению (принадлежит пользователю) */
    @EntityGraph(attributePaths = {"userPlant"})
    List<CareEvent> findAllByUserPlant_IdAndUserPlant_User_IdOrderByEventTimeDesc(Long userPlantId, Long userId);

    /** Этап 8 (P0): REST CRUD — доступ к событию только владельцу растения */
    @EntityGraph(attributePaths = {"userPlant"})
    Optional<CareEvent> findByIdAndUserPlant_User_Id(Long id, Long userId);
}