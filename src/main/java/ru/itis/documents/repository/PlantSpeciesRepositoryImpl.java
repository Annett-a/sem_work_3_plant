package ru.itis.documents.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import ru.itis.documents.domain.entity.CareProfile;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.domain.entity.Tag;

import java.util.ArrayList;
import java.util.List;

@Repository
public class PlantSpeciesRepositoryImpl implements PlantSpeciesRepositoryCustom {

    private static final int DEFAULT_LIMIT = 50;

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<PlantSpecies> findSuitableForApartment(
            String q,
            String roomLightLevel,
            Integer maxWaterInterval,
            String tag,
            Integer limit
    ) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<PlantSpecies> cq = cb.createQuery(PlantSpecies.class);
        Root<PlantSpecies> ps = cq.from(PlantSpecies.class);

        Join<PlantSpecies, CareProfile> cp = ps.join("careProfile", JoinType.LEFT);

        List<Predicate> predicates = new ArrayList<>();

        if (StringUtils.hasText(q)) {
            String like = "%" + q.trim().toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(ps.get("name")), like),
                    cb.like(cb.lower(ps.get("latinName")), like)
            ));
        }

        if (StringUtils.hasText(tag)) {
            String tagLower = tag.trim().toLowerCase();

            Subquery<Integer> sq = cq.subquery(Integer.class);
            Root<PlantSpecies> psq = sq.correlate(ps);
            Join<PlantSpecies, Tag> tq = psq.join("tags", JoinType.INNER);

            sq.select(cb.literal(1))
                    .where(cb.equal(cb.lower(tq.get("name")), tagLower));

            predicates.add(cb.exists(sq));
        }

        if (StringUtils.hasText(roomLightLevel)) {
            String like = "%" + roomLightLevel.trim().toLowerCase() + "%";
            predicates.add(cb.isNotNull(cp.get("lightLevel")));
            predicates.add(cb.like(cb.lower(cp.get("lightLevel")), like));
        }

        if (maxWaterInterval != null) {
            predicates.add(cb.isNotNull(cp.get("waterIntervalDays")));
            predicates.add(cb.lessThanOrEqualTo(cp.get("waterIntervalDays"), maxWaterInterval));
        }

        cq.select(ps);

        if (!predicates.isEmpty()) {
            cq.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        Expression<Integer> hasProfile = cb.<Integer>selectCase()
                .when(cb.isNotNull(cp.get("id")), 1)
                .otherwise(0);

        cq.orderBy(
                cb.desc(hasProfile),
                cb.asc(cb.coalesce(cp.get("waterIntervalDays"), 9999)),
                cb.asc(ps.get("name"))
        );

        TypedQuery<PlantSpecies> query = em.createQuery(cq);
        int max = (limit == null || limit <= 0) ? DEFAULT_LIMIT : limit;
        query.setMaxResults(max);
        return query.getResultList();
    }
}