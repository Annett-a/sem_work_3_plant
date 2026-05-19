package ru.itis.documents.integration.plantnet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlantNetIdentifyResponse(
        List<Result> results,
        @JsonProperty("remainingIdentificationRequests") Integer remainingIdentificationRequests
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            double score,
            Species species
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Species(
            @JsonProperty("scientificNameWithoutAuthor") String scientificNameWithoutAuthor,
            @JsonProperty("scientificName") String scientificName,
            @JsonProperty("commonNames") List<String> commonNames
    ) {
    }
}