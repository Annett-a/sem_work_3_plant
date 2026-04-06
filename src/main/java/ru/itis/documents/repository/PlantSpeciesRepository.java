package ru.itis.documents.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itis.documents.domain.entity.PlantSpecies;

import java.util.List;
import java.util.Optional;

public interface PlantSpeciesRepository extends JpaRepository<PlantSpecies, Long>, PlantSpeciesRepositoryCustom {

    @Override
    @EntityGraph(attributePaths = {"careProfile", "tags"})
    List<PlantSpecies> findAll();

    @Override
    @EntityGraph(attributePaths = {"careProfile", "tags"})
    Optional<PlantSpecies> findById(Long id);

    @EntityGraph(attributePaths = {"careProfile", "tags"})
    Optional<PlantSpecies> findByExternalId(Long externalId);

    boolean existsByExternalId(Long externalId);

    /**
     * Этап 9.2 (P0): нестандартный метод через @Query (JPQL).
     *
     * "Топ капризных видов" с фильтрами. Метод не дублирует findAll/findById:
     * - добавлены опциональные фильтры
     * - добавлена сортировка по "капризности" (тег + параметры ухода)
     * - ограничение результата задаётся Pageable (top N)
     */
    @EntityGraph(attributePaths = {"careProfile"}) // важно: БЕЗ "tags"
    @Query("""
select ps
from PlantSpecies ps
left join ps.careProfile cp
where (:q = '' or lower(ps.name) like concat('%', :q, '%'))
  and (:tag = '' or exists (
        select 1
        from PlantSpecies ps2
        join ps2.tags t
        where ps2 = ps and lower(t.name) = :tag
  ))
  and (:light = '' or (cp.lightLevel is not null and lower(cp.lightLevel) like concat('%', :light, '%')))
  and (:maxWaterInterval is null or (cp.waterIntervalDays is not null and cp.waterIntervalDays <= :maxWaterInterval))
order by
  case when exists (
        select 1
        from PlantSpecies ps3
        join ps3.tags t2
        where ps3 = ps and lower(t2.name) = 'капризное'
  ) then 1 else 0 end desc,
  coalesce(cp.waterIntervalDays, 9999) asc,
  ps.name asc
""")
    List<PlantSpecies> findTopCapriciousSpecies(
            @Param("q") String q,
            @Param("tag") String tag,
            @Param("light") String light,
            @Param("maxWaterInterval") Integer maxWaterInterval,
            Pageable pageable
    );

}