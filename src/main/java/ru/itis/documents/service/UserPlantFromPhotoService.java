package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.documents.form.UserPlantByPhotoForm;
import ru.itis.documents.form.UserPlantCreateForm;

@Service
@RequiredArgsConstructor
public class UserPlantFromPhotoService {

    private final PlantIdentificationService plantIdentificationService;
    private final PlantRecognitionImportService plantRecognitionImportService;
    private final UserPlantService userPlantService;

    @Transactional
    public Long createFromPhoto(String username, Long userId, Long identId, UserPlantByPhotoForm plantForm) {
        plantIdentificationService.selectScientificName(
                username,
                identId,
                plantForm.getSelectedScientificName()
        );

        Long speciesId;
        if (plantForm.getSpeciesId() != null && plantForm.getSpeciesId() > 0) {
            speciesId = plantForm.getSpeciesId();
        } else {
            speciesId = plantRecognitionImportService.importSelectedCandidateToCatalog(
                    username,
                    identId,
                    plantForm.getPerenualId()
            );
        }

        UserPlantCreateForm createForm = new UserPlantCreateForm();
        createForm.setSpeciesId(speciesId);
        createForm.setRoomId(plantForm.getRoomId());
        createForm.setNickname(plantForm.getNickname());
        createForm.setPurchaseDate(plantForm.getPurchaseDate());
        createForm.setNotes(plantForm.getNotes());

        return userPlantService.create(userId, createForm);
    }
}