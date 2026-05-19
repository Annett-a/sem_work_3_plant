package ru.itis.documents.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import ru.itis.documents.dto.view.PlantnetCandidateAjaxView;
import ru.itis.documents.integration.plantnet.PlantNetClient;
import ru.itis.documents.service.PlantNetAjaxIdentifyService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlantNetAjaxIdentifyServiceTest {

    @Mock
    PlantNetClient plantNetClient;
    @InjectMocks
    PlantNetAjaxIdentifyService service;

    @Test
    void identifyCandidates_readsMultipartAndMapsResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile("photo", "plant.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(plantNetClient.identify(file.getBytes(), "plant.jpg")).thenReturn(new PlantNetClient.IdentifyResult(
                "Ficus",
                List.of(new PlantNetClient.Candidate("Ficus elastica", List.of("rubber plant"), 0.98)),
                10,
                null,
                "{}"
        ));

        List<PlantnetCandidateAjaxView> result = service.identifyCandidates(file);

        assertThat(result).containsExactly(new PlantnetCandidateAjaxView("Ficus elastica", 0.98, List.of("rubber plant")));
    }

    @Test
    void identifyCandidates_throwsWhenMultipartCannotBeRead() throws Exception {
        org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getBytes()).thenThrow(new java.io.IOException("boom"));

        assertThatThrownBy(() -> service.identifyCandidates(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("прочитать файл");
    }

    @Test
    void identifyCandidates_throwsWhenFileIsNull() {
        assertThatThrownBy(() -> service.identifyCandidates((org.springframework.web.multipart.MultipartFile) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("прочитать файл");
    }

    @Test
    void identifyCandidates_mapsByteArrayResponseWithNullScientificNameAndNullCommonNames() {
        when(plantNetClient.identify(new byte[]{1}, "saved.jpg")).thenReturn(new PlantNetClient.IdentifyResult(
                null,
                List.of(new PlantNetClient.Candidate(null, null, 0.42)),
                null,
                null,
                "{}"
        ));

        List<PlantnetCandidateAjaxView> result = service.identifyCandidates(new byte[]{1}, "saved.jpg");
        assertThat(result).containsExactly(new PlantnetCandidateAjaxView("(unknown)", 0.42, null));
    }

    @Test
    void identifyCandidates_returnsEmptyListWhenPlantNetCandidatesNull() {
        when(plantNetClient.identify(new byte[]{1}, "saved.jpg")).thenReturn(new PlantNetClient.IdentifyResult(
                null, null, null, null, "{}"
        ));
        assertThat(service.identifyCandidates(new byte[]{1}, "saved.jpg")).isEmpty();
    }

    @Test
    void identifyCandidates_mapsBlankScientificNameAndEmptyCommonNames() {
        when(plantNetClient.identify(new byte[]{9}, "saved.jpg")).thenReturn(new PlantNetClient.IdentifyResult(
                null,
                List.of(new PlantNetClient.Candidate("   ", List.of(), 0.11)),
                null,
                null,
                "{}"
        ));

        List<PlantnetCandidateAjaxView> result = service.identifyCandidates(new byte[]{9}, "saved.jpg");

        assertThat(result).containsExactly(new PlantnetCandidateAjaxView("(unknown)", 0.11, null));
    }
}
