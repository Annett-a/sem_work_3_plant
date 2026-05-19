package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.documents.domain.entity.CareProfile;
import ru.itis.documents.domain.entity.CareTask;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.domain.entity.UserPlant;
import ru.itis.documents.domain.enums.CareActionType;
import ru.itis.documents.domain.enums.CareTaskStatus;
import ru.itis.documents.dto.view.CapriciousnessView;
import ru.itis.documents.repository.CareTaskRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CarePlanService {

    private static final int DEFAULT_WATER_INTERVAL_DAYS = 7;
    private static final int DEFAULT_FERTILIZE_INTERVAL_DAYS = 30;
    private static final int DEFAULT_REPOT_INTERVAL_DAYS = 365;

    private final CareTaskRepository careTaskRepository;
    private final CapriciousnessService capriciousnessService;

    @Transactional
    public void generateInitialPlan(UserPlant plant) {
        if (plant == null || plant.getId() == null) return;

        if (careTaskRepository.existsByUserPlant_Id(plant.getId())) return;

        PlantSpecies sp = plant.getSpecies();
        CareProfile cp = (sp == null) ? null : sp.getCareProfile();

        LocalDate base = (plant.getPurchaseDate() != null) ? plant.getPurchaseDate() : LocalDate.now();

        int waterInterval = (cp != null && cp.getWaterIntervalDays() != null && cp.getWaterIntervalDays() > 0)
                ? cp.getWaterIntervalDays()
                : DEFAULT_WATER_INTERVAL_DAYS;

        CapriciousnessView cap = capriciousnessService.evaluate(sp);

        int fertInterval = fertilizeIntervalByScore(cap.score());
        int repotInterval = repotIntervalByScore(cap.score());

        List<CareTask> tasks = new ArrayList<>();
        tasks.add(buildTask(plant, CareActionType.WATER, computeNextDue(base, waterInterval), "Полив"));
        tasks.add(buildTask(plant, CareActionType.FERTILIZE, computeNextDue(base, fertInterval), "Подкормка"));
        tasks.add(buildTask(plant, CareActionType.REPOT, computeNextDue(base, repotInterval), "Пересадка"));

        careTaskRepository.saveAll(tasks);
    }

    @Transactional
    public void applyEvent(UserPlant plant, CareActionType type, OffsetDateTime eventTime) {
        if (plant == null || plant.getId() == null) return;

        OffsetDateTime eventTs = (eventTime != null) ? eventTime : OffsetDateTime.now();
        int interval = intervalDaysFor(plant, type);

        careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(
                plant.getId(), type, CareTaskStatus.PLANNED
        ).ifPresent(t -> {
            t.setStatus(CareTaskStatus.DONE);
            t.setCompletedAt(eventTs);
            careTaskRepository.save(t);
        });

        CareTask next = new CareTask();
        next.setUserPlant(plant);
        next.setType(type);
        next.setStatus(CareTaskStatus.PLANNED);

        if (type == CareActionType.WATER) {
            next.setDueDate(computeNextDue(eventTs.toLocalDate(), interval));
        } else {
            next.setDueDate(eventTs.toLocalDate().plusDays(Math.max(interval, 1)));
        }

        next.setNote("Авто-план после события: " + type);
        careTaskRepository.save(next);
    }

    private int intervalDaysFor(UserPlant plant, CareActionType type) {
        PlantSpecies sp = plant.getSpecies();
        CareProfile cp = (sp == null) ? null : sp.getCareProfile();
        CapriciousnessView cap = capriciousnessService.evaluate(sp);

        if (type == CareActionType.WATER) {
            Integer w = (cp == null) ? null : cp.getWaterIntervalDays();
            return (w != null && w > 0) ? w : DEFAULT_WATER_INTERVAL_DAYS;
        }
        if (type == CareActionType.FERTILIZE) {
            return fertilizeIntervalByScore(cap.score());
        }
        if (type == CareActionType.REPOT) {
            return repotIntervalByScore(cap.score());
        }
        return 30;
    }

    private static CareTask buildTask(UserPlant plant, CareActionType type, LocalDate due, String note) {
        CareTask t = new CareTask();
        t.setUserPlant(plant);
        t.setType(type);
        t.setDueDate(due);
        t.setNote(note);
        return t;
    }

    private static LocalDate computeNextDue(LocalDate base, int intervalDays) {
        if (intervalDays <= 0) intervalDays = 1;

        LocalDate due = base.plusDays(intervalDays);
        LocalDate today = LocalDate.now();

        if (!due.isBefore(today)) return due;

        long diff = ChronoUnit.DAYS.between(due, today);
        long steps = diff / intervalDays + 1;
        return due.plusDays(steps * intervalDays);
    }

    private static int fertilizeIntervalByScore(int score) {
        if (score >= 70) return 14;
        if (score <= 30) return 45;
        return DEFAULT_FERTILIZE_INTERVAL_DAYS;
    }

    private static int repotIntervalByScore(int score) {
        if (score >= 70) return 180;
        return DEFAULT_REPOT_INTERVAL_DAYS;
    }
}