package ru.itis.documents.service;

import org.junit.jupiter.api.Test;
import ru.itis.documents.domain.entity.CareProfile;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.domain.entity.Tag;
import ru.itis.documents.dto.view.CapriciousnessView;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CapriciousnessServiceTest {

    private final CapriciousnessService service = new CapriciousnessService();
    private long nextTagId = 1;

    @Test
    void evaluate_returnsDefaultMidForNullSpecies() {
        CapriciousnessView result = service.evaluate((PlantSpecies) null);
        assertThat(result.key()).isEqualTo("MID");
        assertThat(result.score()).isEqualTo(50);
        assertThat(result.reasons()).containsExactly("Нет данных о виде");
    }

    @Test
    void evaluate_speciesOverload_usesEmptyTagListWhenSpeciesTagsAreNull() {
        PlantSpecies species = new PlantSpecies();
        species.setTags(null);

        CapriciousnessView result = service.evaluate(species);

        assertThat(result.key()).isEqualTo("MID");
        assertThat(result.score()).isEqualTo(50);
        assertThat(result.reasons()).containsExactly("Средние требования без особых условий");
    }

    @Test
    void evaluate_returnsHighWhenDemandingTagsAndCareProfile() {
        PlantSpecies species = new PlantSpecies();
        species.setTags(Set.of(tag("капризное"), tag("тропическое"), tag("влаголюбивое")));
        CareProfile care = new CareProfile();
        care.setWaterIntervalDays(3);
        care.setHumidityPercent(75);
        care.setLightLevel("яркий свет");
        care.setNotes("нужно опрыскивать каждый день");
        species.setCareProfile(care);

        CapriciousnessView result = service.evaluate(species);

        assertThat(result.key()).isEqualTo("HIGH");
        assertThat(result.score()).isGreaterThanOrEqualTo(70);
        assertThat(result.reasons()).anySatisfy(r -> assertThat(r).contains("капризное"));
        assertThat(result.reasons()).anySatisfy(r -> assertThat(r).contains("Частый полив"));
        assertThat(result.reasons()).anySatisfy(r -> assertThat(r).contains("высокая влажность"));
    }

    @Test
    void evaluate_returnsLowForBeginnerFriendlyPlant() {
        CareProfile care = new CareProfile();
        care.setWaterIntervalDays(20);
        PlantSpecies species = new PlantSpecies();
        species.setTags(Set.of(tag("для новичков"), tag("засухоустойчивое"), tag("теневыносливое")));
        species.setCareProfile(care);

        CapriciousnessView result = service.evaluate(species);

        assertThat(result.key()).isEqualTo("LOW");
        assertThat(result.score()).isLessThanOrEqualTo(30);
    }

    @Test
    void evaluate_addsReasonForBrightLightTagFromSpeciesTags() {
        PlantSpecies species = new PlantSpecies();
        species.setTags(Set.of(tag("яркий свет")));

        CapriciousnessView result = service.evaluate(species);

        assertThat(result.score()).isEqualTo(55);
        assertThat(result.reasons()).containsExactly("Тег: «яркий свет» (важно правильно поставить растение)");
    }

    @Test
    void evaluate_addsRegularWateringReasonForIntervalFromFourToSevenDays() {
        CareProfile care = new CareProfile();
        care.setWaterIntervalDays(6);

        CapriciousnessView result = service.evaluate(List.of(), care);

        assertThat(result.reasons()).anyMatch(r -> r.contains("Регулярный полив"));
    }

    @Test
    void evaluate_addsDryAirReasonForHumidityAtFortyPercentOrLower() {
        CareProfile care = new CareProfile();
        care.setHumidityPercent(40);

        CapriciousnessView result = service.evaluate(List.of(), care);

        assertThat(result.reasons()).anyMatch(r -> r.contains("сухому воздуху"));
    }

    @Test
    void evaluate_addsExtraReasonsForBrightLightAndCareNotes() {
        CareProfile care = new CareProfile();
        care.setLightLevel("яркий свет без полуденного солнца");
        care.setNotes("нужно увлажнение и опрыскивание");

        CapriciousnessView result = service.evaluate(List.of(), care);

        assertThat(result.reasons()).anyMatch(r -> r.contains("Требования к свету"));
        assertThat(result.reasons()).anyMatch(r -> r.contains("дополнительные условия"));
    }

    @Test
    void evaluate_nonBlankLightAndNotesWithoutKeywordsDoNotAddReasons() {
        CareProfile care = new CareProfile();
        care.setLightLevel("рассеянное освещение у окна");
        care.setNotes("периодически поворачивать горшок");

        CapriciousnessView result = service.evaluate(List.of(), care);

        assertThat(result.key()).isEqualTo("MID");
        assertThat(result.score()).isEqualTo(50);
        assertThat(result.reasons()).containsExactly("Средние требования без особых условий");
    }

    @Test
    void evaluate_addsReasonsForFloweringAndToxicTags() {
        CapriciousnessView result = service.evaluate(List.of("цветущее", "токсично для животных"), null);

        assertThat(result.reasons()).anyMatch(r -> r.contains("цветущее"));
        assertThat(result.reasons()).anyMatch(r -> r.contains("токсично"));
    }

    @Test
    void evaluate_clampsScoreToHundredWhenManyDemandingSignalsPresent() {
        CareProfile care = new CareProfile();
        care.setWaterIntervalDays(1);
        care.setHumidityPercent(90);
        care.setLightLevel("прямой яркий свет");
        care.setNotes("обязательно опрыскивание и увлажнение");

        CapriciousnessView result = service.evaluate(
                List.of("капризное", "тропическое", "влаголюбивое", "яркий свет", "цветущее", "токсично для животных"),
                care
        );

        assertThat(result.key()).isEqualTo("HIGH");
        assertThat(result.score()).isEqualTo(100);
    }

    @Test
    void evaluate_neutralWaterAndHumidityDoNotAddReasons() {
        CareProfile care = new CareProfile();
        care.setWaterIntervalDays(10);
        care.setHumidityPercent(55);

        CapriciousnessView result = service.evaluate(List.of(), care);

        assertThat(result.key()).isEqualTo("MID");
        assertThat(result.score()).isEqualTo(50);
        assertThat(result.reasons()).containsExactly("Средние требования без особых условий");
    }

    @Test
    void evaluate_returnsHighAtExactBoundaryScoreSeventy() {
        CareProfile care = new CareProfile();
        care.setWaterIntervalDays(7);
        care.setHumidityPercent(40);
        care.setLightLevel("яркий свет");
        care.setNotes("нужно опрыскивание");

        CapriciousnessView result = service.evaluate(List.of(), care);

        assertThat(result.score()).isEqualTo(70);
        assertThat(result.key()).isEqualTo("HIGH");
    }

    @Test
    void evaluate_returnsLowAtExactBoundaryScoreThirty() {
        CapriciousnessView result = service.evaluate(List.of("для новичков"), null);

        assertThat(result.score()).isEqualTo(30);
        assertThat(result.key()).isEqualTo("LOW");
    }

    @Test
    void evaluate_returnsDefaultReasonWhenNoSignalsFound() {
        CapriciousnessView result = service.evaluate(List.of(), null);
        assertThat(result.reasons()).containsExactly("Средние требования без особых условий");
    }

    @Test
    void evaluate_returnsDefaultReasonWhenTagListIsNull() {
        CapriciousnessView result = service.evaluate((List<String>) null, null);
        assertThat(result.reasons()).containsExactly("Средние требования без особых условий");
    }

    @Test
    void evaluate_normalizesSpeciesTagsAndIgnoresBlankOrNullNames() {
        PlantSpecies species = new PlantSpecies();
        species.setTags(Set.of(tag("  КАПРИЗНОЕ  "), tag(" "), tag(null)));

        CapriciousnessView result = service.evaluate(species);

        assertThat(result.key()).isEqualTo("HIGH");
        assertThat(result.reasons()).anyMatch(r -> r.contains("капризное"));
    }


    @Test
    void evaluate_blankLightAndBlankNotesAreIgnored() {
        CareProfile care = new CareProfile();
        care.setLightLevel("   ");
        care.setNotes("   ");

        CapriciousnessView result = service.evaluate(List.of(), care);

        assertThat(result.key()).isEqualTo("MID");
        assertThat(result.score()).isEqualTo(50);
        assertThat(result.reasons()).containsExactly("Средние требования без особых условий");
    }

    @Test
    void evaluate_addsReasonWhenNotesContainOnlyHumidityKeyword() {
        CareProfile care = new CareProfile();
        care.setNotes("нужно увлажнение воздуха");

        CapriciousnessView result = service.evaluate(List.of(), care);

        assertThat(result.score()).isEqualTo(55);
        assertThat(result.reasons()).containsExactly("Есть дополнительные условия в заметках по уходу");
    }

    private Tag tag(String name) {
        Tag tag = new Tag();
        tag.setId(nextTagId++);
        tag.setName(name);
        return tag;
    }
}
