package ru.itis.documents.config;

import org.junit.jupiter.api.Test;
import ru.itis.documents.config.LightLevelConverter;
import ru.itis.documents.domain.enums.LightLevel;

import static org.assertj.core.api.Assertions.assertThat;

class LightLevelConverterTest {

    private final LightLevelConverter converter = new LightLevelConverter();

    @Test
    void convert_returnsEnumForRussianLabelsAndEnumNames() {
        assertThat(converter.convert("яркий свет")).isEqualTo(LightLevel.BRIGHT);
        assertThat(converter.convert("полутень")).isEqualTo(LightLevel.PART_SHADE);
        assertThat(converter.convert("тень")).isEqualTo(LightLevel.SHADE);
        assertThat(converter.convert("BRIGHT")).isEqualTo(LightLevel.BRIGHT);
        assertThat(converter.convert("part_shade")).isEqualTo(LightLevel.PART_SHADE);
    }

    @Test
    void convert_supportsSoftSynonyms() {
        assertThat(converter.convert("нужен яркий свет у окна")).isEqualTo(LightLevel.BRIGHT);
        assertThat(converter.convert("любит полутень")).isEqualTo(LightLevel.PART_SHADE);
        assertThat(converter.convert("растет в тени")).isEqualTo(LightLevel.SHADE);
    }

    @Test
    void convert_returnsNullForNullBlankAndUnknownValue() {
        assertThat(converter.convert(null)).isNull();
        assertThat(converter.convert("   ")).isNull();
        assertThat(converter.convert("непонятно")).isNull();
    }
}
