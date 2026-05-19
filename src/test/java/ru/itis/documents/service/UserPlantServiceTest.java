package ru.itis.documents.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itis.documents.domain.entity.*;
import ru.itis.documents.domain.enums.CareActionType;
import ru.itis.documents.domain.enums.CareTaskStatus;
import ru.itis.documents.dto.view.CapriciousnessView;
import ru.itis.documents.dto.view.SelectOptionView;
import ru.itis.documents.dto.view.UserPlantCardView;
import ru.itis.documents.dto.view.UserPlantDetailsView;
import ru.itis.documents.form.UserPlantCreateForm;
import ru.itis.documents.form.UserPlantUpdateForm;
import ru.itis.documents.repository.*;
import ru.itis.documents.service.CapriciousnessService;
import ru.itis.documents.service.CarePlanService;
import ru.itis.documents.service.UserPlantService;

import java.lang.reflect.Method;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPlantServiceTest {

    @Mock
    UserPlantRepository userPlantRepository;
    @Mock
    PlantSpeciesRepository plantSpeciesRepository;
    @Mock
    RoomRepository roomRepository;
    @Mock
    AppUserRepository appUserRepository;
    @Mock
    CapriciousnessService capriciousnessService;
    @Mock
    CarePlanService carePlanService;
    @Mock
    CareTaskRepository careTaskRepository;
    @Mock
    PhotoRepository photoRepository;
    @Mock
    FileStorageService fileStorageService;

    @InjectMocks
    UserPlantService service;

    @Test
    void listMyPlants_mapsCardsAndShowsFutureWateringText() {
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", 5, "яркий свет");
        UserPlant plant = plant(10L, "Домашний фикус", species, room(2L, "Гостиная"));
        plant.setPurchaseDate(LocalDate.now().minusDays(2));
        when(userPlantRepository.findAllByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(plant));
        mockCapriciousness(species, "MID", "Средняя", 50);

        UserPlantCardView card = service.listMyPlants(1L).get(0);

        assertThat(card.getNickname()).isEqualTo("Домашний фикус");
        assertThat(card.getRoomName()).isEqualTo("Гостиная");
        assertThat(card.getNextWateringText()).contains("Полив через");
    }

    @Test
    void listMyPlants_returnsNoWateringDataWhenCareProfileMissing() {
        PlantSpecies species = new PlantSpecies();
        species.setId(1L);
        species.setName("Фикус");
        UserPlant plant = plant(10L, "Фикус", species, null);
        when(userPlantRepository.findAllByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(plant));
        mockCapriciousness(species, "MID", "Средняя", 50);

        assertThat(service.listMyPlants(1L).get(0).getNextWateringText()).isEqualTo("Полив: нет данных");
    }

    @Test
    void listMyPlants_returnsNoWateringDataWhenIntervalMissing() {
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", null, "яркий свет");
        UserPlant plant = plant(10L, "Фикус", species, null);
        when(userPlantRepository.findAllByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(plant));
        mockCapriciousness(species, "MID", "Средняя", 50);

        assertThat(service.listMyPlants(1L).get(0).getNextWateringText()).isEqualTo("Полив: нет данных");
    }

    @Test
    void listMyPlants_returnsGenericScheduleWhenPurchaseDateMissing() {
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", 7, "яркий свет");
        UserPlant plant = plant(10L, "Фикус", species, null);
        plant.setPurchaseDate(null);
        when(userPlantRepository.findAllByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(plant));
        mockCapriciousness(species, "MID", "Средняя", 50);

        assertThat(service.listMyPlants(1L).get(0).getNextWateringText()).isEqualTo("Полив каждые 7 дн.");
    }

    @Test
    void listMyPlants_returnsTodayWateringText() {
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", 2, "яркий свет");
        UserPlant plant = plant(10L, "Фикус", species, null);
        plant.setPurchaseDate(LocalDate.now().minusDays(2));
        when(userPlantRepository.findAllByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(plant));
        mockCapriciousness(species, "MID", "Средняя", 50);

        assertThat(service.listMyPlants(1L).get(0).getNextWateringText()).isEqualTo("Полив сегодня");
    }

    @Test
    void listMyPlants_returnsOverdueWateringText() {
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", 2, "яркий свет");
        UserPlant plant = plant(10L, "Фикус", species, null);
        plant.setPurchaseDate(LocalDate.now().minusDays(5));
        when(userPlantRepository.findAllByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(plant));
        mockCapriciousness(species, "MID", "Средняя", 50);

        assertThat(service.listMyPlants(1L).get(0).getNextWateringText()).contains("просрочен");
    }


    @Test
    void listMyPlants_mapsCardWhenSpeciesIsNull() {
        UserPlant plant = plant(10L, "Без вида", null, room(2L, "Гостиная"));
        when(userPlantRepository.findAllByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(plant));
        when(capriciousnessService.evaluate((PlantSpecies) isNull())).thenReturn(new CapriciousnessView("LOW", "Низкая", null, 10, List.of()));

        UserPlantCardView card = service.listMyPlants(1L).get(0);

        assertThat(card.getSpeciesName()).isNull();
        assertThat(card.getRoomName()).isEqualTo("Гостиная");
        assertThat(card.getNextWateringText()).isEqualTo("Полив: нет данных");
        assertThat(card.getCap().score()).isEqualTo(10);
    }

    @Test
    void listMyPlants_returnsEmptyListWhenUserHasNoPlants() {
        when(userPlantRepository.findAllByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        assertThat(service.listMyPlants(1L)).isEmpty();
    }

    @Test
    void getMyPlantDetails_returnsEmptyWhenPlantMissing() {
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());
        assertThat(service.getMyPlantDetails(1L, 10L)).isEmpty();
    }

    @Test
    void getMyPlantDetails_mapsDetailsWithPlannedTasksAndNextWateringFromPlan() {
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", 7, "яркий свет");
        UserPlant plant = plant(10L, "Фикус", species, room(2L, "Гостиная"));
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(10L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.of(task(101L, CareActionType.WATER, CareTaskStatus.PLANNED, LocalDate.now().plusDays(3))));
        when(careTaskRepository.findAllByUserPlant_IdOrderByDueDateAsc(10L)).thenReturn(List.of(
                task(101L, CareActionType.WATER, CareTaskStatus.PLANNED, LocalDate.now().plusDays(3)),
                task(102L, CareActionType.FERTILIZE, CareTaskStatus.PLANNED, LocalDate.now().plusDays(10)),
                task(103L, CareActionType.PRUNE, CareTaskStatus.DONE, LocalDate.now().minusDays(1))
        ));
        mockCapriciousness(species, "HIGH", "Высокая", 80);

        UserPlantDetailsView details = service.getMyPlantDetails(1L, 10L).orElseThrow();

        assertThat(details.getDisplayName()).isEqualTo("Фикус");
        assertThat(details.getRoomName()).isEqualTo("Гостиная");
        assertThat(details.getTasks()).hasSize(2);
        assertThat(details.getNextWateringText()).contains("через");
    }

    @Test
    void getMyPlantDetails_returnsOverdueWateringTextFromPlan() {
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", 7, "яркий свет");
        UserPlant plant = plant(10L, "Фикус", species, null);
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(10L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.of(task(101L, CareActionType.WATER, CareTaskStatus.PLANNED, LocalDate.now().minusDays(2))));
        when(careTaskRepository.findAllByUserPlant_IdOrderByDueDateAsc(10L)).thenReturn(List.of());
        mockCapriciousness(species, "MID", "Средняя", 50);

        assertThat(service.getMyPlantDetails(1L, 10L).orElseThrow().getNextWateringText()).contains("просрочен на 2");
    }

    @Test
    void getMyPlantDetails_returnsFutureWateringTextFromPlan() {
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", 7, "яркий свет");
        UserPlant plant = plant(10L, "Фикус", species, null);
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(10L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.of(task(101L, CareActionType.WATER, CareTaskStatus.PLANNED, LocalDate.now().plusDays(2))));
        when(careTaskRepository.findAllByUserPlant_IdOrderByDueDateAsc(10L)).thenReturn(List.of());
        mockCapriciousness(species, "MID", "Средняя", 50);

        assertThat(service.getMyPlantDetails(1L, 10L).orElseThrow().getNextWateringText()).contains("через 2");
    }

    @Test
    void getMyPlantDetails_returnsNoWateringDataWhenPlannedWaterTaskMissing() {
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", 7, "яркий свет");
        UserPlant plant = plant(10L, "Фикус", species, null);
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(10L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.empty());
        when(careTaskRepository.findAllByUserPlant_IdOrderByDueDateAsc(10L)).thenReturn(List.of());
        mockCapriciousness(species, "MID", "Средняя", 50);

        assertThat(service.getMyPlantDetails(1L, 10L).orElseThrow().getNextWateringText()).isEqualTo("Полив: нет данных");
    }

    @Test
    void getMyPlantDetails_returnsNoWateringDataWhenPlannedWaterTaskHasNullDueDate() {
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", 7, "яркий свет");
        UserPlant plant = plant(10L, "Фикус", species, null);
        CareTask noDate = task(101L, CareActionType.WATER, CareTaskStatus.PLANNED, null);
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(10L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.of(noDate));
        when(careTaskRepository.findAllByUserPlant_IdOrderByDueDateAsc(10L)).thenReturn(List.of());
        mockCapriciousness(species, "MID", "Средняя", 50);

        assertThat(service.getMyPlantDetails(1L, 10L).orElseThrow().getNextWateringText()).isEqualTo("Полив: нет данных");
    }


    @Test
    void getMyPlantDetails_mapsNullNicknameToSpeciesNameAndNullLatinToNull() {
        PlantSpecies species = species(1L, "Фикус", null, 7, "яркий свет");
        UserPlant plant = plant(10L, null, species, null);
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(10L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.of(task(101L, CareActionType.WATER, CareTaskStatus.PLANNED, LocalDate.now())));
        when(careTaskRepository.findAllByUserPlant_IdOrderByDueDateAsc(10L)).thenReturn(List.of());
        mockCapriciousness(species, "MID", "Средняя", 50);

        UserPlantDetailsView details = service.getMyPlantDetails(1L, 10L).orElseThrow();

        assertThat(details.getNickname()).isNull();
        assertThat(details.getDisplayName()).isEqualTo("Фикус");
        assertThat(details.getSpeciesLatinName()).isNull();
        assertThat(details.getDisplaySpeciesLatinName()).isNull();
        assertThat(details.getNextWateringText()).contains("Полив сегодня");
    }

    @Test
    void getMyPlantDetails_mapsBlankNicknameToSpeciesNameAndBlankLatinToNull() {
        PlantSpecies species = species(1L, "Фикус", "   ", 7, "яркий свет");
        UserPlant plant = plant(10L, "   ", species, null);
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(10L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.of(task(101L, CareActionType.WATER, CareTaskStatus.PLANNED, LocalDate.now())));
        when(careTaskRepository.findAllByUserPlant_IdOrderByDueDateAsc(10L)).thenReturn(List.of());
        mockCapriciousness(species, "MID", "Средняя", 50);

        UserPlantDetailsView details = service.getMyPlantDetails(1L, 10L).orElseThrow();

        assertThat(details.getDisplayName()).isEqualTo("Фикус");
        assertThat(details.getDisplaySpeciesLatinName()).isNull();
        assertThat(details.getNextWateringText()).contains("Полив сегодня");
    }

    @Test
    void getMyPlantDetails_mapsNullSpeciesToNullFields() {
        UserPlant plant = plant(null, "   ", null, room(2L, "Гостиная"));
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(careTaskRepository.findAllByUserPlant_IdOrderByDueDateAsc(null)).thenReturn(List.of(
                task(1L, CareActionType.FERTILIZE, CareTaskStatus.PLANNED, LocalDate.now()),
                task(2L, CareActionType.WATER, CareTaskStatus.DONE, LocalDate.now())
        ));
        when(capriciousnessService.evaluate((PlantSpecies) isNull())).thenReturn(new CapriciousnessView("LOW", "Низкая", null, 10, List.of()));

        UserPlantDetailsView details = service.getMyPlantDetails(1L, 10L).orElseThrow();

        assertThat(details.getSpeciesId()).isNull();
        assertThat(details.getSpeciesName()).isNull();
        assertThat(details.getSpeciesLatinName()).isNull();
        assertThat(details.getDisplayName()).isNull();
        assertThat(details.getDisplaySpeciesLatinName()).isNull();
        assertThat(details.getWaterIntervalDays()).isNull();
        assertThat(details.getLightLevel()).isNull();
        assertThat(details.getRoomId()).isEqualTo(2L);
        assertThat(details.getRoomName()).isEqualTo("Гостиная");
        assertThat(details.getNextWateringText()).isEqualTo("Полив: нет данных");
        assertThat(details.getTasks()).hasSize(1);
        assertThat(details.getTasks().get(0).typeLabel()).isEqualTo("Подкормка");
        verify(careTaskRepository, never()).findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(any(), any(), any());
    }

    @Test
    void getMyPlantDetails_mapsAllTaskTypeLabelsIncludingNullType() {
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", 7, "яркий свет");
        UserPlant plant = plant(10L, "Фикус", species, null);
        CareTask nullType = task(101L, null, CareTaskStatus.PLANNED, LocalDate.now());
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(10L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.empty());
        when(careTaskRepository.findAllByUserPlant_IdOrderByDueDateAsc(10L)).thenReturn(List.of(
                task(1L, CareActionType.WATER, CareTaskStatus.PLANNED, LocalDate.now()),
                task(2L, CareActionType.FERTILIZE, CareTaskStatus.PLANNED, LocalDate.now()),
                task(3L, CareActionType.REPOT, CareTaskStatus.PLANNED, LocalDate.now()),
                task(4L, CareActionType.PRUNE, CareTaskStatus.PLANNED, LocalDate.now()),
                task(5L, CareActionType.SPRAY, CareTaskStatus.PLANNED, LocalDate.now()),
                nullType
        ));
        mockCapriciousness(species, "MID", "Средняя", 50);

        assertThat(service.getMyPlantDetails(1L, 10L).orElseThrow().getTasks())
                .extracting(t -> t.typeLabel())
                .containsExactly("Полив", "Подкормка", "Пересадка", "Обрезка", "Опрыскивание", "Уход");
    }

    @Test
    void create_savesPlantWithoutRoomWhenFormDoesNotContainRoom() {
        UserPlantCreateForm form = new UserPlantCreateForm();
        form.setSpeciesId(1L);
        form.setNickname("  Фикус  ");
        form.setPurchaseDate(LocalDate.of(2026, 3, 1));
        form.setNotes("   ");
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", 7, "яркий свет");
        AppUser user = user(1L);
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.of(species));
        when(appUserRepository.getReferenceById(1L)).thenReturn(user);
        when(userPlantRepository.save(any(UserPlant.class))).thenAnswer(inv -> {
            UserPlant p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });

        Long id = service.create(1L, form);

        assertThat(id).isEqualTo(99L);
        verify(userPlantRepository).save(org.mockito.ArgumentMatchers.argThat(p -> p.getRoom() == null && p.getNickname().equals("Фикус") && p.getNotes() == null));
        verify(carePlanService).generateInitialPlan(any(UserPlant.class));
    }

    @Test
    void create_savesPlantNormalizesFieldsAndGeneratesPlan() {
        UserPlantCreateForm form = new UserPlantCreateForm();
        form.setSpeciesId(1L);
        form.setRoomId(2L);
        form.setNickname("  Фикус  ");
        form.setPurchaseDate(LocalDate.of(2026, 3, 1));
        form.setNotes("  note  ");
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", 7, "яркий свет");
        AppUser user = user(1L);
        Room room = room(2L, "Гостиная");
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.of(species));
        when(roomRepository.findByIdAndUser_Id(2L, 1L)).thenReturn(Optional.of(room));
        when(appUserRepository.getReferenceById(1L)).thenReturn(user);
        when(userPlantRepository.save(any(UserPlant.class))).thenAnswer(inv -> {
            UserPlant p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        Long id = service.create(1L, form);

        assertThat(id).isEqualTo(100L);
        verify(userPlantRepository).save(org.mockito.ArgumentMatchers.argThat(p -> p.getRoom() == room && p.getNotes().equals("note")));
        verify(carePlanService).generateInitialPlan(any(UserPlant.class));
    }


    @Test
    void create_ignoresRoomObjectWithoutId() {
        UserPlantCreateForm form = new UserPlantCreateForm();
        form.setSpeciesId(1L);
        form.setRoomId(null);
        form.setNickname("  Фикус  ");
        PlantSpecies species = species(1L, "Фикус", "Ficus elastica", 7, "яркий свет");
        AppUser user = user(1L);
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.of(species));
        when(appUserRepository.getReferenceById(1L)).thenReturn(user);
        when(userPlantRepository.save(any(UserPlant.class))).thenAnswer(inv -> {
            UserPlant p = inv.getArgument(0);
            p.setId(101L);
            return p;
        });

        Long id = service.create(1L, form);

        assertThat(id).isEqualTo(101L);
        verify(roomRepository, never()).findByIdAndUser_Id(anyLong(), anyLong());
        verify(userPlantRepository).save(argThat(p -> p.getRoom() == null));
    }

    @Test
    void create_throwsWhenSpeciesMissing() {
        UserPlantCreateForm form = new UserPlantCreateForm();
        form.setSpeciesId(1L);
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(1L, form)).isInstanceOf(UserPlantService.SpeciesNotFoundException.class);
    }

    @Test
    void create_throwsWhenRoomMissing() {
        UserPlantCreateForm form = new UserPlantCreateForm();
        form.setSpeciesId(1L);
        form.setRoomId(2L);
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.of(species(1L, "Фикус", "Ficus elastica", 7, "яркий свет")));
        when(roomRepository.findByIdAndUser_Id(2L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(1L, form)).isInstanceOf(UserPlantService.RoomNotFoundException.class);
    }

    @Test
    void update_updatesPlantAndNormalizesFields() {
        UserPlant plant = plant(10L, "old", species(1L, "old", "old", 7, "яркий свет"), null);
        UserPlantUpdateForm form = new UserPlantUpdateForm();
        form.setSpeciesId(2L);
        form.setRoomId(2L);
        form.setNickname("  new  ");
        form.setNotes("  note  ");
        PlantSpecies newSpecies = species(2L, "Новый", "New plant", 5, "полутень");
        Room room = room(2L, "Кухня");
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(plantSpeciesRepository.findById(2L)).thenReturn(Optional.of(newSpecies));
        when(roomRepository.findByIdAndUser_Id(2L, 1L)).thenReturn(Optional.of(room));

        service.update(1L, 10L, form);

        assertThat(plant.getSpecies()).isSameAs(newSpecies);
        assertThat(plant.getRoom()).isSameAs(room);
        assertThat(plant.getNickname()).isEqualTo("new");
        assertThat(plant.getNotes()).isEqualTo("note");
        verify(userPlantRepository).save(plant);
    }

    @Test
    void update_clearsRoomWhenFormDoesNotContainRoom() {
        UserPlant plant = plant(10L, "old", species(1L, "old", "old", 7, "яркий свет"), room(2L, "Кухня"));
        UserPlantUpdateForm form = new UserPlantUpdateForm();
        form.setSpeciesId(1L);
        form.setNickname("new");
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.of(plant.getSpecies()));

        service.update(1L, 10L, form);

        assertThat(plant.getRoom()).isNull();
    }


    @Test
    void update_ignoresRoomObjectWithoutIdAndNormalizesNullables() {
        UserPlant plant = plant(10L, "old", species(1L, "old", "old", 7, "яркий свет"), room(2L, "Кухня"));
        UserPlantUpdateForm form = new UserPlantUpdateForm();
        form.setSpeciesId(1L);
        form.setRoomId(null);
        form.setNickname(null);
        form.setNotes("   ");
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.of(plant.getSpecies()));

        service.update(1L, 10L, form);

        assertThat(plant.getRoom()).isNull();
        assertThat(plant.getNickname()).isEqualTo("");
        assertThat(plant.getNotes()).isNull();
        verify(roomRepository, never()).findByIdAndUser_Id(anyLong(), anyLong());
    }

    @Test
    void update_throwsWhenPlantMissing() {
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(1L, 10L, new UserPlantUpdateForm())).isInstanceOf(UserPlantService.UserPlantNotFoundException.class);
    }

    @Test
    void update_throwsWhenSpeciesMissing() {
        UserPlant plant = plant(10L, "old", species(1L, "old", "old", 7, "яркий свет"), null);
        UserPlantUpdateForm form = new UserPlantUpdateForm();
        form.setSpeciesId(2L);
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(plantSpeciesRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(1L, 10L, form)).isInstanceOf(UserPlantService.SpeciesNotFoundException.class);
    }

    @Test
    void update_throwsWhenRoomMissing() {
        UserPlant plant = plant(10L, "old", species(1L, "old", "old", 7, "яркий свет"), null);
        UserPlantUpdateForm form = new UserPlantUpdateForm();
        form.setSpeciesId(1L);
        form.setRoomId(2L);
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.of(plant.getSpecies()));
        when(roomRepository.findByIdAndUser_Id(2L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(1L, 10L, form)).isInstanceOf(UserPlantService.RoomNotFoundException.class);
    }

    @Test
    void delete_deletesOwnedPlant() {
        UserPlant plant = plant(10L, "old", species(1L, "old", "old", 7, "яркий свет"), null);
        Photo photo1 = new Photo();
        photo1.setId(100L);
        photo1.setStorageKey("661111111111111111111111");

        Photo photo2 = new Photo();
        photo2.setId(101L);
        photo2.setStorageKey("662222222222222222222222");

        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(photoRepository.findAllByUserPlant_IdOrderByUploadedAtDesc(10L))
                .thenReturn(List.of(photo1, photo2));

        service.delete(1L, 10L);

        verify(photoRepository).findAllByUserPlant_IdOrderByUploadedAtDesc(10L);
        verify(fileStorageService).delete("661111111111111111111111");
        verify(fileStorageService).delete("662222222222222222222222");
        verify(userPlantRepository).delete(plant);
    }

    @Test
    void delete_deletesOwnedPlantWithoutPhotos() {
        UserPlant plant = plant(10L, "old", species(1L, "old", "old", 7, "яркий свет"), null);
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(photoRepository.findAllByUserPlant_IdOrderByUploadedAtDesc(10L)).thenReturn(List.of());

        service.delete(1L, 10L);

        verify(photoRepository).findAllByUserPlant_IdOrderByUploadedAtDesc(10L);
        verify(fileStorageService, never()).delete(anyString());
        verify(userPlantRepository).delete(plant);
    }

    @Test
    void delete_throwsWhenPlantMissing() {
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 10L))
                .isInstanceOf(UserPlantService.UserPlantNotFoundException.class);

        verifyNoInteractions(photoRepository, fileStorageService);
    }

    @Test
    void speciesOptions_sortsAndBuildsLabels() {
        PlantSpecies a = species(2L, "фикус", "Ficus elastica", 7, "яркий свет");
        PlantSpecies b = species(1L, "алоэ", null, 10, "тень");
        when(plantSpeciesRepository.findAll()).thenReturn(List.of(a, b));

        List<SelectOptionView> result = service.speciesOptions();

        assertThat(result).extracting(SelectOptionView::getLabel).containsExactly("алоэ", "фикус (Ficus elastica)");
    }

    @Test
    void speciesOptions_returnsEmptyListWhenRepositoryHasNoSpecies() {
        when(plantSpeciesRepository.findAll()).thenReturn(List.of());
        assertThat(service.speciesOptions()).isEmpty();
    }

    @Test
    void roomOptions_createsMissingDefaultRoomsAndReturnsSortedOptions() {
        when(roomRepository.findAllByUser_IdOrderByNameAsc(1L)).thenReturn(
                List.of(room(1L, "Гостиная"), room(2L, "Кухня")),
                List.of(room(1L, "Балкон"), room(2L, "Гостиная"), room(3L, "Кухня"))
        );
        when(appUserRepository.getReferenceById(1L)).thenReturn(user(1L));
        when(roomRepository.saveAll(any())).thenReturn(List.of());

        List<SelectOptionView> result = service.roomOptions(1L);

        assertThat(result).extracting(SelectOptionView::getLabel).contains("Балкон", "Гостиная", "Кухня");
        verify(roomRepository).saveAll(org.mockito.ArgumentMatchers.anyIterable());
    }

    @Test
    void roomOptions_doesNotCreateAnythingWhenDefaultsAlreadyExist() {
        List<Room> defaults = List.of(
                room(1L, "Балкон"), room(2L, "Ванная"), room(3L, "Гостиная"), room(4L, "Детская"),
                room(5L, "Кабинет"), room(6L, "Коридор"), room(7L, "Кухня"), room(8L, "Лоджия"),
                room(9L, "Подоконник"), room(10L, "Спальня")
        );
        when(roomRepository.findAllByUser_IdOrderByNameAsc(1L)).thenReturn(defaults);

        List<SelectOptionView> result = service.roomOptions(1L);

        assertThat(result).hasSize(10);
        verify(roomRepository, never()).saveAll(any());
    }

    @Test
    void roomOptions_treatsRoomNamesCaseInsensitivelyAndWithTrimmedSpaces() {
        when(roomRepository.findAllByUser_IdOrderByNameAsc(1L)).thenReturn(
                List.of(room(1L, " кухня "), room(2L, "ГОСТИНАЯ")),
                List.of(room(1L, "Балкон"), room(2L, "ГОСТИНАЯ"), room(3L, " кухня "))
        );
        when(appUserRepository.getReferenceById(1L)).thenReturn(user(1L));

        service.roomOptions(1L);

        verify(roomRepository).saveAll(org.mockito.ArgumentMatchers.anyIterable());
    }


    @Test
    void roomOptions_ignoresExistingRoomsWithNullName() {
        Room unnamed = new Room();
        unnamed.setId(99L);
        unnamed.setName(null);
        when(roomRepository.findAllByUser_IdOrderByNameAsc(1L)).thenReturn(
                List.of(unnamed, room(1L, "Гостиная")),
                List.of(unnamed, room(1L, "Гостиная"), room(2L, "Кухня"))
        );
        when(appUserRepository.getReferenceById(1L)).thenReturn(user(1L));

        List<SelectOptionView> result = service.roomOptions(1L);

        assertThat(result).extracting(SelectOptionView::getLabel).containsNull();
        verify(roomRepository).saveAll(anyIterable());
    }

    @Test
    void privateHelpers_coverNormalizeEqualsTypeLabelSpeciesLabelAndWateringBranches() throws Exception {
        Method normalizeRequired = UserPlantService.class.getDeclaredMethod("normalizeRequired", String.class);
        Method normalizeNullable = UserPlantService.class.getDeclaredMethod("normalizeNullable", String.class);
        Method equalsIgnoreCase = UserPlantService.class.getDeclaredMethod("equalsIgnoreCase", String.class, String.class);
        Method typeLabel = UserPlantService.class.getDeclaredMethod("typeLabel", CareActionType.class);
        Method buildSpeciesLabel = UserPlantService.class.getDeclaredMethod("buildSpeciesLabel", PlantSpecies.class);
        Method nextWateringText = UserPlantService.class.getDeclaredMethod("nextWateringText", LocalDate.class, CareProfile.class);
        Method nextWateringTextFromPlan = UserPlantService.class.getDeclaredMethod("nextWateringTextFromPlan", Long.class);

        normalizeRequired.setAccessible(true);
        normalizeNullable.setAccessible(true);
        equalsIgnoreCase.setAccessible(true);
        typeLabel.setAccessible(true);
        buildSpeciesLabel.setAccessible(true);
        nextWateringText.setAccessible(true);
        nextWateringTextFromPlan.setAccessible(true);

        assertThat(normalizeRequired.invoke(null, new Object[]{null})).isEqualTo("");
        assertThat(normalizeRequired.invoke(null, "  x  ")).isEqualTo("x");

        assertThat(normalizeNullable.invoke(null, new Object[]{null})).isNull();
        assertThat(normalizeNullable.invoke(null, "   ")).isNull();
        assertThat(normalizeNullable.invoke(null, "  note  ")).isEqualTo("note");

        assertThat((boolean) equalsIgnoreCase.invoke(null, null, "a")).isFalse();
        assertThat((boolean) equalsIgnoreCase.invoke(null, "a", null)).isFalse();
        assertThat((boolean) equalsIgnoreCase.invoke(null, "HeLLo", "hello")).isTrue();
        assertThat((boolean) equalsIgnoreCase.invoke(null, "HeLLo", "world")).isFalse();

        assertThat(typeLabel.invoke(null, new Object[]{null})).isEqualTo("Уход");
        assertThat(typeLabel.invoke(null, CareActionType.WATER)).isEqualTo("Полив");
        assertThat(typeLabel.invoke(null, CareActionType.FERTILIZE)).isEqualTo("Подкормка");
        assertThat(typeLabel.invoke(null, CareActionType.REPOT)).isEqualTo("Пересадка");
        assertThat(typeLabel.invoke(null, CareActionType.PRUNE)).isEqualTo("Обрезка");
        assertThat(typeLabel.invoke(null, CareActionType.SPRAY)).isEqualTo("Опрыскивание");

        PlantSpecies noLatin = new PlantSpecies();
        noLatin.setName("Фикус");
        noLatin.setLatinName("   ");
        assertThat(buildSpeciesLabel.invoke(null, noLatin)).isEqualTo("Фикус");

        PlantSpecies withLatin = new PlantSpecies();
        withLatin.setName("Фикус");
        withLatin.setLatinName("Ficus elastica");
        assertThat(buildSpeciesLabel.invoke(null, withLatin)).isEqualTo("Фикус (Ficus elastica)");

        assertThat(nextWateringText.invoke(null, null, null)).isEqualTo("Полив: нет данных");

        CareProfile noDays = new CareProfile();
        noDays.setWaterIntervalDays(null);
        assertThat(nextWateringText.invoke(null, LocalDate.now(), noDays)).isEqualTo("Полив: нет данных");

        CareProfile cp = new CareProfile();
        cp.setWaterIntervalDays(5);
        assertThat(nextWateringText.invoke(null, null, cp)).isEqualTo("Полив каждые 5 дн.");

        assertThat(nextWateringTextFromPlan.invoke(service, new Object[]{null})).isEqualTo("Полив: нет данных");
        when(careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(10L, CareActionType.WATER, CareTaskStatus.PLANNED))
                .thenReturn(Optional.of(task(201L, CareActionType.WATER, CareTaskStatus.PLANNED, LocalDate.now())));
        assertThat(nextWateringTextFromPlan.invoke(service, 10L)).isEqualTo("Полив сегодня (" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ")");
    }

    private void mockCapriciousness(PlantSpecies species, String key, String label, int score) {
        when(capriciousnessService.evaluate(species)).thenReturn(new CapriciousnessView(key, label, null, score, List.of()));
    }

    private CareTask task(Long id, CareActionType type, CareTaskStatus status, LocalDate dueDate) {
        CareTask task = new CareTask();
        task.setId(id);
        task.setType(type);
        task.setStatus(status);
        task.setDueDate(dueDate);
        return task;
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser();
        user.setId(id);
        return user;
    }

    private UserPlant plant(Long id, String nickname, PlantSpecies species, Room room) {
        UserPlant plant = new UserPlant();
        plant.setId(id);
        plant.setNickname(nickname);
        plant.setSpecies(species);
        plant.setRoom(room);
        plant.setCreatedAt(OffsetDateTime.now());
        return plant;
    }

    private PlantSpecies species(Long id, String name, String latinName, Integer waterDays, String lightLevel) {
        PlantSpecies species = new PlantSpecies();
        species.setId(id);
        species.setName(name);
        species.setLatinName(latinName);
        CareProfile careProfile = new CareProfile();
        careProfile.setWaterIntervalDays(waterDays);
        careProfile.setLightLevel(lightLevel);
        species.setCareProfile(careProfile);
        return species;
    }

    private Room room(Long id, String name) {
        Room room = new Room();
        room.setId(id);
        room.setName(name);
        return room;
    }
}
