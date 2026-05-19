package ru.itis.documents.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlantSpeciesApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void catalogReturnsSeededSpeciesFromDatabase() throws Exception {
        mockMvc.perform(get("/api/species"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$[*].name", hasItem("Сансевиерия")))
                .andExpect(jsonPath("$[*].name", hasItem("Спатифиллум")))
                .andExpect(jsonPath("$[*].name", hasItem("Фикус Бенджамина")))
                .andExpect(jsonPath("$[*].name", hasItem("Замиокулькас")));
    }

    @Test
    void catalogCanFilterSpeciesByQuery() throws Exception {
        mockMvc.perform(get("/api/species")
                        .param("q", "Сансевиерия"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Сансевиерия"))
                .andExpect(jsonPath("$[0].latinName").value("Sansevieria trifasciata"));
    }
}