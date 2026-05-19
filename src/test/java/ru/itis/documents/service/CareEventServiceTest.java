package ru.itis.documents.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itis.documents.domain.entity.CareEvent;
import ru.itis.documents.domain.entity.UserPlant;
import ru.itis.documents.domain.enums.CareActionType;
import ru.itis.documents.repository.CareEventRepository;
import ru.itis.documents.repository.UserPlantRepository;
import ru.itis.documents.service.CareEventService;
import ru.itis.documents.service.CarePlanService;
import java.lang.reflect.Method;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareEventServiceTest {

    @Mock UserPlantRepository userPlantRepository;
    @Mock CareEventRepository careEventRepository;
    @Mock
    CarePlanService carePlanService;

    @InjectMocks
    CareEventService service;

    @Test
    void addWatering_savesEventAndRecalculatesPlan() {
        UserPlant plant = plant(10L);
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(careEventRepository.save(any(CareEvent.class))).thenAnswer(inv -> {
            CareEvent e = inv.getArgument(0);
            e.setId(50L);
            e.setEventTime(OffsetDateTime.parse("2026-03-25T10:00:00Z"));
            return e;
        });

        service.addWatering(1L, 10L, "  after work  ");

        ArgumentCaptor<CareEvent> captor = ArgumentCaptor.forClass(CareEvent.class);
        verify(careEventRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(CareActionType.WATER);
        assertThat(captor.getValue().getComment()).isEqualTo("after work");
        verify(carePlanService).applyEvent(plant, CareActionType.WATER, OffsetDateTime.parse("2026-03-25T10:00:00Z"));
    }

    @Test
    void addWatering_throwsWhenPlantNotFound() {
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addWatering(1L, 10L, "x"))
                .isInstanceOf(CareEventService.UserPlantNotFoundException.class);
    }

    @Test
    void listMyPlantEvents_checksOwnershipAndReturnsEvents() {
        UserPlant plant = plant(10L);
        List<CareEvent> events = List.of(new CareEvent(), new CareEvent());
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(careEventRepository.findAllByUserPlant_IdOrderByEventTimeDesc(10L)).thenReturn(events);

        assertThat(service.listMyPlantEvents(1L, 10L)).containsExactlyElementsOf(events);
    }

    @Test
    void listMyPlantEvents_throwsWhenPlantMissing() {
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listMyPlantEvents(1L, 10L))
                .isInstanceOf(CareEventService.UserPlantNotFoundException.class);
    }

    @Test
    void listMyEvents_withoutPlantFilterReturnsAllUserEvents() {
        List<CareEvent> events = List.of(new CareEvent());
        when(careEventRepository.findAllByUserPlant_User_IdOrderByEventTimeDesc(1L)).thenReturn(events);
        assertThat(service.listMyEvents(1L, null)).containsExactlyElementsOf(events);
    }

    @Test
    void listMyEvents_withPlantFilterChecksOwnership() {
        UserPlant plant = plant(10L);
        List<CareEvent> events = List.of(new CareEvent());
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(careEventRepository.findAllByUserPlant_IdAndUserPlant_User_IdOrderByEventTimeDesc(10L, 1L)).thenReturn(events);
        assertThat(service.listMyEvents(1L, 10L)).containsExactlyElementsOf(events);
    }

    @Test
    void listMyEvents_withPlantFilterThrowsWhenPlantMissing() {
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listMyEvents(1L, 10L))
                .isInstanceOf(CareEventService.UserPlantNotFoundException.class);
    }

    @Test
    void getMyEvent_returnsOwnedEvent() {
        CareEvent event = new CareEvent();
        event.setId(99L);
        when(careEventRepository.findByIdAndUserPlant_User_Id(99L, 1L)).thenReturn(Optional.of(event));
        assertThat(service.getMyEvent(1L, 99L)).isSameAs(event);
    }

    @Test
    void getMyEvent_throwsWhenMissing() {
        when(careEventRepository.findByIdAndUserPlant_User_Id(99L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getMyEvent(1L, 99L))
                .isInstanceOf(CareEventService.CareEventNotFoundException.class);
    }

    @Test
    void createEvent_throwsWhenTypeMissing() {
        assertThatThrownBy(() -> service.createEvent(1L, 10L, null, OffsetDateTime.now(), "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void createEvent_savesNormalizedCommentAndRecalculatesPlan() {
        UserPlant plant = plant(10L);
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(careEventRepository.save(any(CareEvent.class))).thenAnswer(inv -> {
            CareEvent e = inv.getArgument(0);
            e.setId(55L);
            return e;
        });
        OffsetDateTime ts = OffsetDateTime.parse("2026-03-25T11:00:00Z");

        CareEvent result = service.createEvent(1L, 10L, CareActionType.FERTILIZE, ts, "  note ");

        assertThat(result.getType()).isEqualTo(CareActionType.FERTILIZE);
        assertThat(result.getComment()).isEqualTo("note");
        verify(carePlanService).applyEvent(plant, CareActionType.FERTILIZE, ts);
    }

    @Test
    void createEvent_throwsWhenPlantMissing() {
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createEvent(1L, 10L, CareActionType.WATER, OffsetDateTime.now(), null))
                .isInstanceOf(CareEventService.UserPlantNotFoundException.class);
    }

    @Test
    void updateEvent_updatesOnlyProvidedFieldsAndNormalizesComment() {
        CareEvent event = new CareEvent();
        event.setId(5L);
        event.setType(CareActionType.WATER);
        event.setComment("old");
        when(careEventRepository.findByIdAndUserPlant_User_Id(5L, 1L)).thenReturn(Optional.of(event));
        when(careEventRepository.save(event)).thenReturn(event);
        OffsetDateTime ts = OffsetDateTime.parse("2026-03-25T12:00:00Z");

        CareEvent result = service.updateEvent(1L, 5L, CareActionType.REPOT, ts, "  changed ");

        assertThat(result.getType()).isEqualTo(CareActionType.REPOT);
        assertThat(result.getEventTime()).isEqualTo(ts);
        assertThat(result.getComment()).isEqualTo("changed");
    }

    @Test
    void updateEvent_keepsTypeAndTimeWhenNullsPassed() {
        CareEvent event = new CareEvent();
        event.setId(5L);
        event.setType(CareActionType.WATER);
        OffsetDateTime ts = OffsetDateTime.parse("2026-03-25T12:00:00Z");
        event.setEventTime(ts);
        when(careEventRepository.findByIdAndUserPlant_User_Id(5L, 1L)).thenReturn(Optional.of(event));
        when(careEventRepository.save(event)).thenReturn(event);

        CareEvent result = service.updateEvent(1L, 5L, null, null, "   ");

        assertThat(result.getType()).isEqualTo(CareActionType.WATER);
        assertThat(result.getEventTime()).isEqualTo(ts);
        assertThat(result.getComment()).isNull();
    }

    @Test
    void updateEvent_throwsWhenMissing() {
        when(careEventRepository.findByIdAndUserPlant_User_Id(5L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateEvent(1L, 5L, null, null, null))
                .isInstanceOf(CareEventService.CareEventNotFoundException.class);
    }

    @Test
    void deleteEvent_deletesOwnedEvent() {
        CareEvent event = new CareEvent();
        when(careEventRepository.findByIdAndUserPlant_User_Id(5L, 1L)).thenReturn(Optional.of(event));
        service.deleteEvent(1L, 5L);
        verify(careEventRepository).delete(event);
    }

    @Test
    void deleteEvent_throwsWhenMissing() {
        when(careEventRepository.findByIdAndUserPlant_User_Id(5L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteEvent(1L, 5L))
                .isInstanceOf(CareEventService.CareEventNotFoundException.class);
    }

    @Test
    void normalizeNullable_coversNullBlankAndTrimmedBranches() throws Exception {
        Method m = CareEventService.class.getDeclaredMethod("normalizeNullable", String.class);
        m.setAccessible(true);

        assertThat(m.invoke(null, new Object[]{null})).isNull();
        assertThat(m.invoke(null, "   ")).isNull();
        assertThat(m.invoke(null, "  note  ")).isEqualTo("note");
    }

    private UserPlant plant(Long id) {
        UserPlant plant = new UserPlant();
        plant.setId(id);
        return plant;
    }
}
