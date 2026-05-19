package ru.itis.documents.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itis.documents.domain.entity.CareTask;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.domain.entity.UserPlant;
import ru.itis.documents.domain.enums.CareActionType;
import ru.itis.documents.domain.enums.CareTaskStatus;
import ru.itis.documents.dto.view.StaleWateringPlantRawView;
import ru.itis.documents.dto.view.StaleWateringPlantView;
import ru.itis.documents.dto.view.TodayDueItemView;
import ru.itis.documents.repository.CareTaskRepository;
import ru.itis.documents.repository.UserPlantRepository;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodayDashboardServiceTest {

    @Mock
    CareTaskRepository careTaskRepository;
    @Mock
    UserPlantRepository userPlantRepository;
    @InjectMocks
    TodayDashboardService service;

    @Test
    void getTodayDue_mapsTasksAndOverdueDays() {
        LocalDate today = LocalDate.now();
        CareTask todayTask = task(1L, today, CareActionType.WATER);
        CareTask overdueTask = task(2L, today.minusDays(3), CareActionType.FERTILIZE);
        when(careTaskRepository.findAllByUserPlant_User_IdAndStatusAndDueDateIsNotNullAndDueDateLessThanEqualOrderByDueDateAsc(
                1L, CareTaskStatus.PLANNED, today)).thenReturn(List.of(todayTask, overdueTask));

        List<TodayDueItemView> result = service.getTodayDue(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).overdueDays()).isZero();
        assertThat(result.get(1).overdueDays()).isEqualTo(3);
        assertThat(result.get(1).typeLabel()).isEqualTo("Подкормка");
    }

    @Test
    void getTodayDue_returnsEmptyListWhenNothingIsDue() {
        when(careTaskRepository.findAllByUserPlant_User_IdAndStatusAndDueDateIsNotNullAndDueDateLessThanEqualOrderByDueDateAsc(
                eq(1L), eq(CareTaskStatus.PLANNED), eq(LocalDate.now()))).thenReturn(List.of());
        assertThat(service.getTodayDue(1L)).isEmpty();
    }

    @Test
    void getTodayDue_handlesMissingPlantSpeciesAndTypeSafely() {
        CareTask task = new CareTask();
        task.setId(5L);
        task.setStatus(CareTaskStatus.PLANNED);
        task.setDueDate(LocalDate.now());
        when(careTaskRepository.findAllByUserPlant_User_IdAndStatusAndDueDateIsNotNullAndDueDateLessThanEqualOrderByDueDateAsc(
                eq(1L), eq(CareTaskStatus.PLANNED), eq(LocalDate.now()))).thenReturn(List.of(task));

        TodayDueItemView item = service.getTodayDue(1L).get(0);
        assertThat(item.plantId()).isNull();
        assertThat(item.speciesName()).isNull();
        assertThat(item.typeLabel()).isEqualTo("Уход");
    }

    @Test
    void getTodayDue_handlesPlantWithoutSpeciesButKeepsPlantFields() {
        UserPlant plant = new UserPlant();
        plant.setId(77L);
        plant.setNickname("Монстера");

        CareTask task = new CareTask();
        task.setId(6L);
        task.setUserPlant(plant);
        task.setStatus(CareTaskStatus.PLANNED);
        task.setType(CareActionType.PRUNE);
        task.setDueDate(LocalDate.now().minusDays(1));

        when(careTaskRepository.findAllByUserPlant_User_IdAndStatusAndDueDateIsNotNullAndDueDateLessThanEqualOrderByDueDateAsc(
                eq(1L), eq(CareTaskStatus.PLANNED), eq(LocalDate.now()))).thenReturn(List.of(task));

        TodayDueItemView item = service.getTodayDue(1L).get(0);

        assertThat(item.plantId()).isEqualTo(77L);
        assertThat(item.plantNickname()).isEqualTo("Монстера");
        assertThat(item.speciesName()).isNull();
        assertThat(item.typeLabel()).isEqualTo("Обрезка");
        assertThat(item.overdueDays()).isEqualTo(1);
    }

    @Test
    void getTodayDue_keepsZeroOverdueWhenDueDateIsNull() {
        CareTask task = new CareTask();
        task.setId(7L);
        task.setStatus(CareTaskStatus.PLANNED);
        task.setType(CareActionType.SPRAY);

        when(careTaskRepository.findAllByUserPlant_User_IdAndStatusAndDueDateIsNotNullAndDueDateLessThanEqualOrderByDueDateAsc(
                eq(1L), eq(CareTaskStatus.PLANNED), eq(LocalDate.now()))).thenReturn(List.of(task));

        TodayDueItemView item = service.getTodayDue(1L).get(0);

        assertThat(item.dueDate()).isNull();
        assertThat(item.overdueDays()).isZero();
        assertThat(item.typeLabel()).isEqualTo("Опрыскивание");
    }

    @Test
    void getPlantsWithoutWateringDays_usesSafeDaysAndMapsNullLastWatering() {
        when(userPlantRepository.findPlantsWithoutCareSince(eq(1L), eq(CareActionType.WATER), any(), any()))
                .thenReturn(List.of(new StaleWateringPlantRawView(10L, "Фикус", "Ficus", null)));

        List<StaleWateringPlantView> result = service.getPlantsWithoutWateringDays(1L, 0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).daysWithoutWatering()).isGreaterThan(1000);
    }

    @Test
    void getPlantsWithoutWateringDays_returnsEmptyListWhenRepositoryReturnedNothing() {
        when(userPlantRepository.findPlantsWithoutCareSince(eq(1L), eq(CareActionType.WATER), any(), any()))
                .thenReturn(List.of());
        assertThat(service.getPlantsWithoutWateringDays(1L, 5)).isEmpty();
    }

    @Test
    void getPlantsWithoutWateringDays_usesProvidedPositiveDaysInRepositoryQuery() {
        when(userPlantRepository.findPlantsWithoutCareSince(eq(1L), eq(CareActionType.WATER), any(), any()))
                .thenReturn(List.of(new StaleWateringPlantRawView(10L, "Фикус", "Ficus", OffsetDateTime.now().minusDays(7))));
        service.getPlantsWithoutWateringDays(1L, 7);
        verify(userPlantRepository).findPlantsWithoutCareSince(eq(1L), eq(CareActionType.WATER), any(), any());
    }

    @Test
    void getTodayDue_clampsNegativeOverdueToZeroForUnexpectedFutureTask() {
        CareTask futureTask = task(1L, LocalDate.now().plusDays(3), CareActionType.WATER);
        when(careTaskRepository.findAllByUserPlant_User_IdAndStatusAndDueDateIsNotNullAndDueDateLessThanEqualOrderByDueDateAsc(
                eq(1L), eq(CareTaskStatus.PLANNED), eq(LocalDate.now())
        )).thenReturn(List.of(futureTask));

        TodayDueItemView item = service.getTodayDue(1L).get(0);

        assertThat(item.overdueDays()).isZero();
    }

    @Test
    void privateTypeLabel_coversAllBranches() throws Exception {
        Method typeLabel = TodayDashboardService.class.getDeclaredMethod("typeLabel", CareActionType.class);
        typeLabel.setAccessible(true);

        assertThat(typeLabel.invoke(null, new Object[]{null})).isEqualTo("Уход");
        assertThat(typeLabel.invoke(null, CareActionType.WATER)).isEqualTo("Полив");
        assertThat(typeLabel.invoke(null, CareActionType.FERTILIZE)).isEqualTo("Подкормка");
        assertThat(typeLabel.invoke(null, CareActionType.REPOT)).isEqualTo("Пересадка");
        assertThat(typeLabel.invoke(null, CareActionType.PRUNE)).isEqualTo("Обрезка");
        assertThat(typeLabel.invoke(null, CareActionType.SPRAY)).isEqualTo("Опрыскивание");
    }

    private CareTask task(Long id, LocalDate dueDate, CareActionType type) {
        PlantSpecies species = new PlantSpecies();
        species.setName("Ficus");
        UserPlant plant = new UserPlant();
        plant.setId(10L);
        plant.setNickname("Фикус");
        plant.setSpecies(species);

        CareTask task = new CareTask();
        task.setId(id);
        task.setUserPlant(plant);
        task.setStatus(CareTaskStatus.PLANNED);
        task.setDueDate(dueDate);
        task.setType(type);
        return task;
    }
}
