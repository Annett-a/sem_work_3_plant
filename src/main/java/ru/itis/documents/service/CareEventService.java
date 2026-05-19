package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.documents.domain.entity.CareEvent;
import ru.itis.documents.domain.entity.UserPlant;
import ru.itis.documents.domain.enums.CareActionType;
import ru.itis.documents.repository.CareEventRepository;
import ru.itis.documents.repository.UserPlantRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CareEventService {

    private final UserPlantRepository userPlantRepository;
    private final CareEventRepository careEventRepository;
    private final CarePlanService carePlanService;

    @Transactional
    public void addWatering(Long userId, Long plantId, String comment) {
        UserPlant plant = userPlantRepository.findByIdAndUser_Id(plantId, userId)
                .orElseThrow(() -> new UserPlantNotFoundException("Растение не найдено"));

        CareEvent e = new CareEvent();
        e.setUserPlant(plant);
        e.setType(CareActionType.WATER);
        e.setComment(normalizeNullable(comment));

        careEventRepository.save(e);

        carePlanService.applyEvent(plant, CareActionType.WATER, e.getEventTime());
    }

    @Transactional(readOnly = true)
    public List<CareEvent> listMyPlantEvents(Long userId, Long plantId) {
        userPlantRepository.findByIdAndUser_Id(plantId, userId)
                .orElseThrow(() -> new UserPlantNotFoundException("Растение не найдено"));

        return careEventRepository.findAllByUserPlant_IdOrderByEventTimeDesc(plantId);
    }

    @Transactional(readOnly = true)
    public List<CareEvent> listMyEvents(Long userId, Long plantId) {
        if (plantId == null) {
            return careEventRepository.findAllByUserPlant_User_IdOrderByEventTimeDesc(userId);
        }
        userPlantRepository.findByIdAndUser_Id(plantId, userId)
                .orElseThrow(() -> new UserPlantNotFoundException("Растение не найдено"));

        return careEventRepository.findAllByUserPlant_IdAndUserPlant_User_IdOrderByEventTimeDesc(plantId, userId);
    }

    @Transactional(readOnly = true)
    public CareEvent getMyEvent(Long userId, Long eventId) {
        return careEventRepository.findByIdAndUserPlant_User_Id(eventId, userId)
                .orElseThrow(() -> new CareEventNotFoundException("Событие не найдено"));
    }

    @Transactional
    public CareEvent createEvent(Long userId, Long plantId, CareActionType type, OffsetDateTime eventTime, String comment) {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }

        UserPlant plant = userPlantRepository.findByIdAndUser_Id(plantId, userId)
                .orElseThrow(() -> new UserPlantNotFoundException("Растение не найдено"));

        CareEvent e = new CareEvent();
        e.setUserPlant(plant);
        e.setType(type);
        e.setEventTime(eventTime);
        e.setComment(normalizeNullable(comment));

        careEventRepository.save(e);

        carePlanService.applyEvent(plant, type, e.getEventTime());

        return e;
    }

    @Transactional
    public CareEvent updateEvent(Long userId, Long eventId, CareActionType type, OffsetDateTime eventTime, String comment) {
        CareEvent e = careEventRepository.findByIdAndUserPlant_User_Id(eventId, userId)
                .orElseThrow(() -> new CareEventNotFoundException("Событие не найдено"));

        if (type != null) {
            e.setType(type);
        }
        if (eventTime != null) {
            e.setEventTime(eventTime);
        }
        e.setComment(normalizeNullable(comment));

        return careEventRepository.save(e);
    }

    @Transactional
    public void deleteEvent(Long userId, Long eventId) {
        CareEvent e = careEventRepository.findByIdAndUserPlant_User_Id(eventId, userId)
                .orElseThrow(() -> new CareEventNotFoundException("Событие не найдено"));
        careEventRepository.delete(e);
    }

    private static String normalizeNullable(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static class UserPlantNotFoundException extends RuntimeException {
        public UserPlantNotFoundException(String message) {
            super(message);
        }
    }

    public static class CareEventNotFoundException extends RuntimeException {
        public CareEventNotFoundException(String message) {
            super(message);
        }
    }
}