package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.documents.domain.entity.CareTask;
import ru.itis.documents.domain.entity.UserPlant;
import ru.itis.documents.domain.enums.CareActionType;
import ru.itis.documents.domain.enums.CareTaskStatus;
import ru.itis.documents.dto.view.StaleWateringPlantRawView;
import ru.itis.documents.dto.view.StaleWateringPlantView;
import ru.itis.documents.dto.view.TodayDueItemView;
import ru.itis.documents.repository.CareTaskRepository;
import ru.itis.documents.repository.UserPlantRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TodayDashboardService {

    private final CareTaskRepository careTaskRepository;
    private final UserPlantRepository userPlantRepository;

    @Transactional(readOnly = true)
    public List<TodayDueItemView> getTodayDue(Long userId) {
        LocalDate today = LocalDate.now();

        List<CareTask> tasks = careTaskRepository
                .findAllByUserPlant_User_IdAndStatusAndDueDateIsNotNullAndDueDateLessThanEqualOrderByDueDateAsc(
                        userId,
                        CareTaskStatus.PLANNED,
                        today
                );

        return tasks.stream()
                .map(t -> toView(t, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StaleWateringPlantView> getPlantsWithoutWateringDays(Long userId, int days) {
        int safeDays = Math.max(days, 1);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minusDays(safeDays);
        OffsetDateTime epoch = OffsetDateTime.parse("1970-01-01T00:00:00Z");

        List<StaleWateringPlantRawView> raw = userPlantRepository.findPlantsWithoutCareSince(
                userId,
                CareActionType.WATER,
                cutoff,
                epoch
        );

        return raw.stream()
                .map(r -> {
                    OffsetDateTime last = r.lastWaterTime();
                    OffsetDateTime base = (last == null) ? epoch : last;
                    long d = ChronoUnit.DAYS.between(base.toLocalDate(), now.toLocalDate());
                    return new StaleWateringPlantView(
                            r.plantId(),
                            r.nickname(),
                            r.speciesName(),
                            last,
                            d
                    );
                })
                .toList();
    }

    private TodayDueItemView toView(CareTask t, LocalDate today) {
        UserPlant p = t.getUserPlant();

        String nickname = (p == null) ? null : p.getNickname();
        String speciesName = (p == null || p.getSpecies() == null) ? null : p.getSpecies().getName();

        long overdue = 0;
        if (t.getDueDate() != null) {
            overdue = ChronoUnit.DAYS.between(t.getDueDate(), today);
            if (overdue < 0) overdue = 0;
        }

        return new TodayDueItemView(
                t.getId(),
                (p == null ? null : p.getId()),
                nickname,
                speciesName,
                t.getType(),
                typeLabel(t.getType()),
                t.getDueDate(),
                overdue
        );
    }

    private static String typeLabel(CareActionType type) {
        if (type == null) return "Уход";
        return switch (type) {
            case WATER -> "Полив";
            case FERTILIZE -> "Подкормка";
            case REPOT -> "Пересадка";
            case PRUNE -> "Обрезка";
            case SPRAY -> "Опрыскивание";
        };
    }
}