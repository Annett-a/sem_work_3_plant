package ru.itis.documents.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itis.documents.domain.entity.UserPlant;
import ru.itis.documents.domain.enums.CareActionType;
import ru.itis.documents.dto.view.StaleWateringPlantRawView;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserPlantRepository extends JpaRepository<UserPlant, Long> {

    @EntityGraph(attributePaths = {"species", "species.tags", "species.careProfile", "room"})
    List<UserPlant> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"species", "species.tags", "species.careProfile", "room"})
    Optional<UserPlant> findByIdAndUser_Id(Long id, Long userId);

    /**
     * Этап 9.4 (P0): запрос с подзапросом.
     *
     * "Мои растения без полива N дней": последний CareEvent(WATER) раньше cutoff (или полива не было).
     * Подзапрос: (select max(e.eventTime) ...)
     */
    @Query("""
            select new ru.itis.documents.dto.view.StaleWateringPlantRawView(
              p.id,
              p.nickname,
              s.name,
              (select max(e.eventTime)
               from CareEvent e
               where e.userPlant = p and e.type = :type)
            )
            from UserPlant p
            join p.species s
            where p.user.id = :userId
              and coalesce(
                (select max(e2.eventTime)
                 from CareEvent e2
                 where e2.userPlant = p and e2.type = :type),
                :epoch
              ) < :cutoff
            order by coalesce(
                (select max(e3.eventTime)
                 from CareEvent e3
                 where e3.userPlant = p and e3.type = :type),
                :epoch
              ) asc,
              p.createdAt asc
            """)
    List<StaleWateringPlantRawView> findPlantsWithoutCareSince(
            @Param("userId") Long userId,
            @Param("type") CareActionType type,
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("epoch") OffsetDateTime epoch
    );
}