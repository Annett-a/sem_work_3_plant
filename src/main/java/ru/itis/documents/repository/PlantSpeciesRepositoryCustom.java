package ru.itis.documents.repository;

import ru.itis.documents.domain.entity.PlantSpecies;

import java.util.List;

public interface PlantSpeciesRepositoryCustom {

    /**
     * Подбор видов по условиям (динамические фильтры).
     *
     * @param q                часть названия/латинского названия (nullable/blank)
     * @param roomLightLevel   уровень света в комнате (nullable/blank)
     * @param maxWaterInterval максимальный интервал полива (nullable)
     * @param tag              фильтр по тегу (nullable/blank)
     * @param limit            ограничение результата (если null или <=0, используется 50)
     */
    List<PlantSpecies> findSuitableForApartment(
            String q,
            String roomLightLevel,
            Integer maxWaterInterval,
            String tag,
            Integer limit
    );

}