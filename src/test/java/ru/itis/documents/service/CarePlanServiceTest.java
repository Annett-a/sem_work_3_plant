package ru.itis.documents.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itis.documents.domain.entity.CareProfile;
import ru.itis.documents.domain.entity.CareTask;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.domain.entity.UserPlant;
import ru.itis.documents.domain.enums.CareActionType;
import ru.itis.documents.domain.enums.CareTaskStatus;
import ru.itis.documents.dto.view.CapriciousnessView;
import ru.itis.documents.repository.CareTaskRepository;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarePlanServiceTest {

    @Mock
    CareTaskRepository careTaskRepository;
    @Mock
    CapriciousnessService capriciousnessService;

    @InjectMocks
    CarePlanService service;

    @Test
    void generateInitialPlan_doesNothingForNullPlantOrMissingId() {
        service.generateInitialPlan(null);
        service.generateInitialPlan(new UserPlant());
        verify(careTaskRepository, never()).existsByUserPlant_Id(any());
    }

    @Test
    void generateInitialPlan_skipsWhenPlanAlreadyExists() {
        UserPlant plant = plantWithProfile(1L, 5, "яркий свет");
        when(careTaskRepository.existsByUserPlant_Id(1L)).thenReturn(true);

        service.generateInitialPlan(plant);

        verify(careTaskRepository, never()).saveAll(any());
    }

    @Test
    void generateInitialPlan_createsThreeTasksUsingCareProfileAndHighScoreIntervals() {
        UserPlant plant = plantWithProfile(1L, 4, "яркий свет");
        plant.setPurchaseDate(LocalDate.now().minusDays(1));
        when(careTaskRepository.existsByUserPlant_Id(1L)).thenReturn(false);
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("HIGH", "Высокая", null, 75, List.of()));

        ArgumentCaptor<List<CareTask>> captor = ArgumentCaptor.forClass(List.class);
        service.generateInitialPlan(plant);

        verify(careTaskRepository).saveAll(captor.capture());
        List<CareTask> tasks = captor.getValue();
        assertThat(tasks).hasSize(3);
        assertThat(tasks).extracting(CareTask::getType)
                .containsExactly(CareActionType.WATER, CareActionType.FERTILIZE, CareActionType.REPOT);
        assertThat(tasks.get(0).getDueDate()).isAfterOrEqualTo(LocalDate.now());
        assertThat(tasks.get(1).getDueDate()).isAfterOrEqualTo(LocalDate.now());
        assertThat(tasks.get(2).getDueDate()).isAfterOrEqualTo(LocalDate.now());
    }

    @Test
    void generateInitialPlan_usesDefaultIntervalsWhenCareProfileAndPurchaseDateAreMissing() {
        UserPlant plant = new UserPlant();
        plant.setId(1L);
        plant.setSpecies(new PlantSpecies());
        when(careTaskRepository.existsByUserPlant_Id(1L)).thenReturn(false);
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));

        ArgumentCaptor<List<CareTask>> captor = ArgumentCaptor.forClass(List.class);
        service.generateInitialPlan(plant);

        verify(careTaskRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    void generateInitialPlan_usesDefaultWaterIntervalWhenWaterDaysAreNonPositive() {
        UserPlant plant = plantWithProfile(1L, 0, null);
        when(careTaskRepository.existsByUserPlant_Id(1L)).thenReturn(false);
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));

        ArgumentCaptor<List<CareTask>> captor = ArgumentCaptor.forClass(List.class);
        service.generateInitialPlan(plant);

        verify(careTaskRepository).saveAll(captor.capture());
        CareTask waterTask = captor.getValue().get(0);
        assertThat(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), waterTask.getDueDate()))
                .isBetween(0L, 7L);
    }


    @Test
    void generateInitialPlan_usesDefaultWaterIntervalWhenWaterDaysAreNull() {
        UserPlant plant = plantWithProfile(1L, null, null);
        when(careTaskRepository.existsByUserPlant_Id(1L)).thenReturn(false);
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));

        ArgumentCaptor<List<CareTask>> captor = ArgumentCaptor.forClass(List.class);
        service.generateInitialPlan(plant);

        verify(careTaskRepository).saveAll(captor.capture());
        CareTask waterTask = captor.getValue().get(0);
        assertThat(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), waterTask.getDueDate()))
                .isBetween(0L, 7L);
    }

    @Test
    void generateInitialPlan_worksWhenSpeciesIsNullAndUsesLowScoreIntervals() {
        UserPlant plant = new UserPlant();
        plant.setId(1L);
        plant.setPurchaseDate(LocalDate.now());

        when(careTaskRepository.existsByUserPlant_Id(1L)).thenReturn(false);
        when(capriciousnessService.evaluate(null))
                .thenReturn(new CapriciousnessView("LOW", "Низкая", null, 20, List.of()));

        ArgumentCaptor<List<CareTask>> captor = ArgumentCaptor.forClass(List.class);
        service.generateInitialPlan(plant);

        verify(careTaskRepository).saveAll(captor.capture());
        List<CareTask> tasks = captor.getValue();
        assertThat(tasks).hasSize(3);
        assertThat(tasks.get(0).getDueDate()).isEqualTo(LocalDate.now().plusDays(7));
        assertThat(tasks.get(1).getDueDate()).isEqualTo(LocalDate.now().plusDays(45));
        assertThat(tasks.get(2).getDueDate()).isEqualTo(LocalDate.now().plusDays(365));
    }

    @Test
    void applyEvent_doesNothingForNullPlantOrMissingId() {
        service.applyEvent(null, CareActionType.WATER, OffsetDateTime.now());
        service.applyEvent(new UserPlant(), CareActionType.WATER, OffsetDateTime.now());
        verify(careTaskRepository, never()).findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(any(), any(), any());
    }

    @Test
    void applyEvent_usesDefaultWaterIntervalWhenCareProfileMissing() {
        UserPlant plant = new UserPlant();
        plant.setId(1L);
        plant.setSpecies(new PlantSpecies());
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(1L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.empty());

        OffsetDateTime ts = OffsetDateTime.parse("2026-03-25T10:15:30Z");
        service.applyEvent(plant, CareActionType.WATER, ts);

        ArgumentCaptor<CareTask> nextCaptor = ArgumentCaptor.forClass(CareTask.class);
        verify(careTaskRepository).save(nextCaptor.capture());
        assertThat(nextCaptor.getValue().getDueDate()).isEqualTo(expectedNextDue(ts.toLocalDate(), 7));
    }


    @Test
    void applyEvent_usesDefaultWaterIntervalWhenWaterDaysAreNonPositive() {
        UserPlant plant = plantWithProfile(1L, 0, null);
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(1L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.empty());

        OffsetDateTime ts = OffsetDateTime.parse("2026-03-25T10:15:30Z");
        service.applyEvent(plant, CareActionType.WATER, ts);

        ArgumentCaptor<CareTask> nextCaptor = ArgumentCaptor.forClass(CareTask.class);
        verify(careTaskRepository).save(nextCaptor.capture());
        assertThat(nextCaptor.getValue().getDueDate()).isEqualTo(expectedNextDue(ts.toLocalDate(), 7));
    }

    @Test
    void applyEvent_usesDefaultWaterIntervalWhenSpeciesIsNull() {
        UserPlant plant = new UserPlant();
        plant.setId(1L);
        when(capriciousnessService.evaluate(null))
                .thenReturn(new CapriciousnessView("LOW", "Низкая", null, 20, List.of()));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(1L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.empty());

        OffsetDateTime ts = OffsetDateTime.parse("2026-03-25T10:15:30Z");
        service.applyEvent(plant, CareActionType.WATER, ts);

        ArgumentCaptor<CareTask> nextCaptor = ArgumentCaptor.forClass(CareTask.class);
        verify(careTaskRepository).save(nextCaptor.capture());
        assertThat(nextCaptor.getValue().getType()).isEqualTo(CareActionType.WATER);
        assertThat(nextCaptor.getValue().getDueDate()).isEqualTo(expectedNextDue(ts.toLocalDate(), 7));
    }

    @Test
    void applyEvent_usesShortFertilizeIntervalForHighScore() {
        UserPlant plant = plantWithProfile(1L, 7, null);
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("HIGH", "Высокая", null, 70, List.of()));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(1L, CareActionType.FERTILIZE, CareTaskStatus.PLANNED))
                .thenReturn(Optional.empty());

        OffsetDateTime ts = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        service.applyEvent(plant, CareActionType.FERTILIZE, ts);

        ArgumentCaptor<CareTask> captor = ArgumentCaptor.forClass(CareTask.class);
        verify(careTaskRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(CareActionType.FERTILIZE);
        assertThat(captor.getValue().getDueDate()).isEqualTo(ts.toLocalDate().plusDays(14));
    }

    @Test
    void applyEvent_usesDefaultFertilizeIntervalForMidScore() {
        UserPlant plant = plantWithProfile(1L, 7, null);
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(1L, CareActionType.FERTILIZE, CareTaskStatus.PLANNED))
                .thenReturn(Optional.empty());

        OffsetDateTime ts = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        service.applyEvent(plant, CareActionType.FERTILIZE, ts);

        ArgumentCaptor<CareTask> captor = ArgumentCaptor.forClass(CareTask.class);
        verify(careTaskRepository).save(captor.capture());
        assertThat(captor.getValue().getDueDate()).isEqualTo(ts.toLocalDate().plusDays(30));
    }

    @Test
    void applyEvent_usesLongFertilizeIntervalForLowScore() {
        UserPlant plant = plantWithProfile(1L, 7, null);
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("LOW", "Низкая", null, 20, List.of()));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(1L, CareActionType.FERTILIZE, CareTaskStatus.PLANNED))
                .thenReturn(Optional.empty());

        OffsetDateTime ts = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        service.applyEvent(plant, CareActionType.FERTILIZE, ts);

        ArgumentCaptor<CareTask> captor = ArgumentCaptor.forClass(CareTask.class);
        verify(careTaskRepository).save(captor.capture());
        assertThat(captor.getValue().getDueDate()).isEqualTo(ts.toLocalDate().plusDays(45));
    }

    @Test
    void applyEvent_usesDefaultRepotIntervalForNonHighScore() {
        UserPlant plant = plantWithProfile(1L, 7, null);
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(1L, CareActionType.REPOT, CareTaskStatus.PLANNED))
                .thenReturn(Optional.empty());

        OffsetDateTime ts = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        service.applyEvent(plant, CareActionType.REPOT, ts);

        ArgumentCaptor<CareTask> captor = ArgumentCaptor.forClass(CareTask.class);
        verify(careTaskRepository).save(captor.capture());
        assertThat(captor.getValue().getDueDate()).isEqualTo(ts.toLocalDate().plusDays(365));
    }

    @Test
    void applyEvent_marksExistingTaskDoneAndCreatesNextWaterTask() {
        UserPlant plant = plantWithProfile(1L, 5, null);
        CareTask existing = new CareTask();
        existing.setId(77L);
        existing.setStatus(CareTaskStatus.PLANNED);
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(1L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.of(existing));

        OffsetDateTime ts = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        service.applyEvent(plant, CareActionType.WATER, ts);

        assertThat(existing.getStatus()).isEqualTo(CareTaskStatus.DONE);
        assertThat(existing.getCompletedAt()).isEqualTo(ts);
        verify(careTaskRepository).save(existing);
        verify(careTaskRepository).save(org.mockito.ArgumentMatchers.argThat(t -> t != existing && t.getType() == CareActionType.WATER));
    }

    @Test
    void applyEvent_createsNextTaskEvenWhenNoPlannedTaskExists() {
        UserPlant plant = plantWithProfile(1L, 5, null);
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(1L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.empty());

        service.applyEvent(plant, CareActionType.WATER, OffsetDateTime.parse("2026-03-25T00:00:00Z"));
        verify(careTaskRepository).save(org.mockito.ArgumentMatchers.argThat(t -> t.getStatus() == CareTaskStatus.PLANNED));
    }

    @Test
    void applyEvent_usesRepotIntervalForHighScore() {
        UserPlant plant = plantWithProfile(1L, 7, null);
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("HIGH", "Высокая", null, 70, List.of()));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(1L, CareActionType.REPOT, CareTaskStatus.PLANNED))
                .thenReturn(Optional.empty());

        OffsetDateTime ts = OffsetDateTime.parse("2026-03-25T00:00:00Z");
        service.applyEvent(plant, CareActionType.REPOT, ts);

        verify(careTaskRepository).save(org.mockito.ArgumentMatchers.argThat(t -> t.getDueDate().equals(ts.toLocalDate().plusDays(180))));
    }

    @Test
    void applyEvent_usesDefaultThirtyDaysForOtherActionsAndCurrentTimeWhenNullPassed() {
        UserPlant plant = plantWithProfile(1L, 7, null);
        when(capriciousnessService.evaluate(plant.getSpecies()))
                .thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(1L, CareActionType.SPRAY, CareTaskStatus.PLANNED))
                .thenReturn(Optional.empty());

        service.applyEvent(plant, CareActionType.SPRAY, null);

        verify(careTaskRepository).save(org.mockito.ArgumentMatchers.argThat(t ->
                t.getType() == CareActionType.SPRAY &&
                        !t.getDueDate().isBefore(LocalDate.now().plusDays(30)) &&
                        !t.getDueDate().isAfter(LocalDate.now().plusDays(31))));
    }

    @Test
    void privateStaticHelpers_coverIntervalsAndDateBranches() throws Exception {
        Method computeNextDue = CarePlanService.class.getDeclaredMethod("computeNextDue", LocalDate.class, int.class);
        Method fertilize = CarePlanService.class.getDeclaredMethod("fertilizeIntervalByScore", int.class);
        Method repot = CarePlanService.class.getDeclaredMethod("repotIntervalByScore", int.class);

        computeNextDue.setAccessible(true);
        fertilize.setAccessible(true);
        repot.setAccessible(true);

        LocalDate today = LocalDate.now();

        assertThat((LocalDate) computeNextDue.invoke(null, today, 0)).isEqualTo(today.plusDays(1));
        assertThat((LocalDate) computeNextDue.invoke(null, today, 5)).isEqualTo(today.plusDays(5));
        assertThat((LocalDate) computeNextDue.invoke(null, today.minusDays(20), 7)).isAfterOrEqualTo(today);

        assertThat((int) fertilize.invoke(null, 80)).isEqualTo(14);
        assertThat((int) fertilize.invoke(null, 20)).isEqualTo(45);
        assertThat((int) fertilize.invoke(null, 50)).isEqualTo(30);

        assertThat((int) repot.invoke(null, 80)).isEqualTo(180);
        assertThat((int) repot.invoke(null, 50)).isEqualTo(365);
    }

    private UserPlant plantWithProfile(Long id, Integer waterDays, String light) {
        CareProfile careProfile = new CareProfile();
        careProfile.setWaterIntervalDays(waterDays);
        careProfile.setLightLevel(light);
        PlantSpecies species = new PlantSpecies();
        species.setCareProfile(careProfile);
        UserPlant plant = new UserPlant();
        plant.setId(id);
        plant.setSpecies(species);
        return plant;
    }

    private LocalDate expectedNextDue(LocalDate base, int intervalDays) {
        if (intervalDays <= 0) {
            intervalDays = 1;
        }

        LocalDate due = base.plusDays(intervalDays);
        LocalDate today = LocalDate.now();

        if (!due.isBefore(today)) {
            return due;
        }

        long diff = java.time.temporal.ChronoUnit.DAYS.between(due, today);
        long steps = diff / intervalDays + 1;
        return due.plusDays(steps * intervalDays);
    }
}
