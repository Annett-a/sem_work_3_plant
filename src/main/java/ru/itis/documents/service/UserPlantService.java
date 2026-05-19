package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.documents.domain.entity.*;
import ru.itis.documents.dto.view.SelectOptionView;
import ru.itis.documents.dto.view.UserPlantCardView;
import ru.itis.documents.dto.view.UserPlantDetailsView;
import ru.itis.documents.form.UserPlantCreateForm;
import ru.itis.documents.form.UserPlantUpdateForm;
import ru.itis.documents.repository.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserPlantService {

    private final UserPlantRepository userPlantRepository;
    private final PlantSpeciesRepository plantSpeciesRepository;
    private final RoomRepository roomRepository;
    private final AppUserRepository appUserRepository;
    private final CapriciousnessService capriciousnessService;
    private final CarePlanService carePlanService;
    private final CareTaskRepository careTaskRepository;
    private final PhotoRepository photoRepository;
    private final FileStorageService fileStorageService;

    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final List<String> DEFAULT_ROOM_NAMES = List.of(
            "Балкон",
            "Ванная",
            "Гостиная",
            "Детская",
            "Кабинет",
            "Коридор",
            "Кухня",
            "Лоджия",
            "Подоконник",
            "Спальня"
    );

    @Transactional(readOnly = true)
    public List<UserPlantCardView> listMyPlants(Long userId) {
        return userPlantRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toCardView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<UserPlantDetailsView> getMyPlantDetails(Long userId, Long plantId) {
        return userPlantRepository.findByIdAndUser_Id(plantId, userId).map(this::toDetailsView);
    }

    @Transactional
    public Long create(Long userId, UserPlantCreateForm form) {
        PlantSpecies species = plantSpeciesRepository.findById(form.getSpeciesId())
                .orElseThrow(() -> new SpeciesNotFoundException("Вид растения не найден"));

        Room room = null;
        if (form.getRoomId() != null) {
            room = roomRepository.findByIdAndUser_Id(form.getRoomId(), userId)
                    .orElseThrow(() -> new RoomNotFoundException("Комната не найдена"));
        }

        AppUser userRef = appUserRepository.getReferenceById(userId);

        UserPlant p = new UserPlant();
        p.setUser(userRef);
        p.setSpecies(species);
        p.setRoom(room);
        p.setNickname(normalizeRequired(form.getNickname()));
        p.setPurchaseDate(form.getPurchaseDate());
        p.setNotes(normalizeNullable(form.getNotes()));

        userPlantRepository.save(p);
        carePlanService.generateInitialPlan(p);
        return p.getId();
    }

    @Transactional
    public void update(Long userId, Long plantId, UserPlantUpdateForm form) {
        UserPlant p = userPlantRepository.findByIdAndUser_Id(plantId, userId)
                .orElseThrow(() -> new UserPlantNotFoundException("Растение не найдено"));

        PlantSpecies species = plantSpeciesRepository.findById(form.getSpeciesId())
                .orElseThrow(() -> new SpeciesNotFoundException("Вид растения не найден"));

        Room room = null;
        if (form.getRoomId() != null) {
            room = roomRepository.findByIdAndUser_Id(form.getRoomId(), userId)
                    .orElseThrow(() -> new RoomNotFoundException("Комната не найдена"));
        }

        p.setSpecies(species);
        p.setRoom(room);
        p.setNickname(normalizeRequired(form.getNickname()));
        p.setPurchaseDate(form.getPurchaseDate());
        p.setNotes(normalizeNullable(form.getNotes()));

        userPlantRepository.save(p);
    }

    @Transactional
    public void delete(Long userId, Long plantId) {
        UserPlant p = userPlantRepository.findByIdAndUser_Id(plantId, userId)
                .orElseThrow(() -> new UserPlantNotFoundException("Растение не найдено"));

        List<Photo> photos = photoRepository.findAllByUserPlant_IdOrderByUploadedAtDesc(plantId);
        for (Photo photo : photos) {
            fileStorageService.delete(photo.getStorageKey());
        }

        userPlantRepository.delete(p);
    }

    @Transactional(readOnly = true)
    public List<SelectOptionView> speciesOptions() {
        return plantSpeciesRepository.findAll().stream()
                .sorted(Comparator.comparing(PlantSpecies::getName, String.CASE_INSENSITIVE_ORDER))
                .map(s -> SelectOptionView.builder()
                        .id(s.getId())
                        .label(buildSpeciesLabel(s))
                        .build())
                .toList();
    }

    @Transactional
    public List<SelectOptionView> roomOptions(Long userId) {
        ensureDefaultRooms(userId);

        return roomRepository.findAllByUser_IdOrderByNameAsc(userId).stream()
                .map(r -> SelectOptionView.builder()
                        .id(r.getId())
                        .label(r.getName())
                        .build())
                .toList();
    }

    private UserPlantCardView toCardView(UserPlant p) {
        PlantSpecies sp = p.getSpecies();
        CareProfile cp = (sp == null) ? null : sp.getCareProfile();

        String roomName = (p.getRoom() == null) ? null : p.getRoom().getName();
        CapriciousnessView cap = capriciousnessService.evaluate(sp);
        String nextWatering = nextWateringText(p.getPurchaseDate(), cp);

        return UserPlantCardView.builder()
                .id(p.getId())
                .nickname(p.getNickname())
                .speciesName(sp == null ? null : sp.getName())
                .roomName(roomName)
                .purchaseDate(p.getPurchaseDate())
                .nextWateringText(nextWatering)
                .cap(cap)
                .build();
    }

    private UserPlantDetailsView toDetailsView(UserPlant p) {
        PlantSpecies sp = p.getSpecies();
        CareProfile cp = (sp == null) ? null : sp.getCareProfile();

        String roomName = (p.getRoom() == null) ? null : p.getRoom().getName();
        CapriciousnessView cap = capriciousnessService.evaluate(sp);

        String nextWatering = nextWateringTextFromPlan(p.getId());

        List<CareTaskItemView> tasks = careTaskRepository.findAllByUserPlant_IdOrderByDueDateAsc(p.getId()).stream()
                .filter(t -> t.getStatus() == ru.itis.documents.domain.enums.CareTaskStatus.PLANNED)
                .map(t -> new CareTaskItemView(
                        t.getId(),
                        t.getType(),
                        typeLabel(t.getType()),
                        t.getStatus(),
                        t.getDueDate()
                ))
                .toList();

        String displayName = (p.getNickname() == null || p.getNickname().isBlank())
                ? (sp == null ? null : sp.getName())
                : p.getNickname();

        String displaySpeciesLatinName = (sp == null || sp.getLatinName() == null || sp.getLatinName().isBlank())
                ? null
                : sp.getLatinName();

        return UserPlantDetailsView.builder()
                .id(p.getId())
                .nickname(p.getNickname())
                .speciesId(sp == null ? null : sp.getId())
                .speciesName(sp == null ? null : sp.getName())
                .speciesLatinName(sp == null ? null : sp.getLatinName())
                .displayName(displayName)
                .displaySpeciesLatinName(displaySpeciesLatinName)
                .roomId(p.getRoom() == null ? null : p.getRoom().getId())
                .roomName(roomName)
                .purchaseDate(p.getPurchaseDate())
                .notes(p.getNotes())
                .cap(cap)
                .waterIntervalDays(cp == null ? null : cp.getWaterIntervalDays())
                .lightLevel(cp == null ? null : cp.getLightLevel())
                .nextWateringText(nextWatering)
                .tasks(tasks)
                .build();
    }

    private String nextWateringTextFromPlan(Long plantId) {
        if (plantId == null) return "Полив: нет данных";

        var opt = careTaskRepository.findFirstByUserPlant_IdAndTypeAndStatusOrderByDueDateAsc(
                plantId,
                ru.itis.documents.domain.enums.CareActionType.WATER,
                ru.itis.documents.domain.enums.CareTaskStatus.PLANNED
        );

        if (opt.isEmpty() || opt.get().getDueDate() == null) {
            return "Полив: нет данных";
        }

        LocalDate due = opt.get().getDueDate();
        LocalDate today = LocalDate.now();
        long diff = ChronoUnit.DAYS.between(today, due);

        if (diff == 0) return "Полив сегодня (" + RU_DATE.format(due) + ")";
        if (diff < 0) return "Полив просрочен на " + (-diff) + " дн. (" + RU_DATE.format(due) + ")";
        return "Полив через " + diff + " дн. (" + RU_DATE.format(due) + ")";
    }

    private static String typeLabel(ru.itis.documents.domain.enums.CareActionType type) {
        if (type == null) return "Уход";
        return switch (type) {
            case WATER -> "Полив";
            case FERTILIZE -> "Подкормка";
            case REPOT -> "Пересадка";
            case PRUNE -> "Обрезка";
            case SPRAY -> "Опрыскивание";
        };
    }

    private static String buildSpeciesLabel(PlantSpecies s) {
        if (s.getLatinName() == null || s.getLatinName().isBlank()) {
            return s.getName();
        }
        return s.getName() + " (" + s.getLatinName() + ")";
    }

    private static String nextWateringText(LocalDate purchaseDate, CareProfile cp) {
        if (cp == null || cp.getWaterIntervalDays() == null) {
            return "Полив: нет данных";
        }
        int interval = cp.getWaterIntervalDays();

        if (purchaseDate == null) {
            return "Полив каждые " + interval + " дн.";
        }

        LocalDate next = purchaseDate.plusDays(interval);
        long days = ChronoUnit.DAYS.between(LocalDate.now(), next);
        if (days == 0) return "Полив сегодня";
        if (days < 0) return "Полив просрочен на " + (-days) + " дн.";
        return "Полив через " + days + " дн.";
    }

    private void ensureDefaultRooms(Long userId) {
        List<Room> existingRooms = roomRepository.findAllByUser_IdOrderByNameAsc(userId);

        Set<String> existingNames = new LinkedHashSet<>();
        for (Room room : existingRooms) {
            if (room.getName() != null) {
                existingNames.add(room.getName().trim().toLowerCase(Locale.ROOT));
            }
        }

        AppUser userRef = appUserRepository.getReferenceById(userId);

        List<Room> toCreate = DEFAULT_ROOM_NAMES.stream()
                .filter(name -> !existingNames.contains(name.toLowerCase(Locale.ROOT)))
                .map(name -> {
                    Room room = new Room();
                    room.setUser(userRef);
                    room.setName(name);
                    return room;
                })
                .toList();

        if (!toCreate.isEmpty()) {
            roomRepository.saveAll(toCreate);
        }
    }

    private static String normalizeRequired(String s) {
        if (s == null) return "";
        return s.trim();
    }

    private static String normalizeNullable(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) return false;
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }

    public static class SpeciesNotFoundException extends RuntimeException {
        public SpeciesNotFoundException(String message) {
            super(message);
        }
    }

    public static class RoomNotFoundException extends RuntimeException {
        public RoomNotFoundException(String message) {
            super(message);
        }
    }

    public static class UserPlantNotFoundException extends RuntimeException {
        public UserPlantNotFoundException(String message) {
            super(message);
        }
    }
}