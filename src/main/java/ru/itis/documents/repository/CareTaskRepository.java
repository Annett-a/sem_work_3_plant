package ru.itis.documents.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.documents.domain.entity.CareTask;
import ru.itis.documents.domain.enums.CareActionType;
import ru.itis.documents.domain.enums.CareTaskStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CareTaskRepository extends JpaRepository<CareTask, Long> {

    boolean existsByUserPlant_Id(Long userPlantId);

    List<CareTask> findAllByUserPlant_IdOrderByDueDateAsc(Long userPlantId);

    Optional<CareTask> findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(
            Long userPlantId,
            CareActionType type,
            CareTaskStatus status
    );
    @EntityGraph(attributePaths = {"userPlant", "userPlant.species"})
    List<CareTask> findAllByUserPlant_User_IdAndStatusAndDueDateIsNotNullAndDueDateLessThanEqualOrderByDueDateAsc(
            Long userId,
            CareTaskStatus status,
            LocalDate date
    );
}
