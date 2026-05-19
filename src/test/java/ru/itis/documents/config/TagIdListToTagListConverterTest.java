package ru.itis.documents.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itis.documents.config.TagIdListToTagListConverter;
import ru.itis.documents.domain.entity.Tag;
import ru.itis.documents.repository.TagRepository;

import java.util.Arrays;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagIdListToTagListConverterTest {

    @Mock
    TagRepository tagRepository;

    @InjectMocks
    TagIdListToTagListConverter converter;

    @Test
    void convert_returnsEmptyForNullAndBlankSource() {
        assertThat(converter.convert(null)).isEmpty();
        assertThat(converter.convert("   ")).isEmpty();
        verifyNoInteractions(tagRepository);
    }

    @Test
    void convert_ignoresGarbageAndRestoresSourceOrderWithoutDuplicates() {
        Tag t1 = tag(1L, "один");
        Tag t2 = tag(2L, "два");
        Tag t5 = tag(5L, "пять");
        when(tagRepository.findAllById(anyIterable())).thenReturn(List.of(t5, t2, t1));

        List<Tag> result = converter.convert(" 1, 2, foo, 5, 2, -1, 0, , 1 ");

        assertThat(result).containsExactly(t1, t2, t5);
    }

    @Test
    void convert_returnsEmptyWhenNothingFoundOrNoValidIds() {
        assertThat(converter.convert("foo, -2, 0, , bar")).isEmpty();

        when(tagRepository.findAllById(anyIterable())).thenReturn(List.of());
        assertThat(converter.convert("1,2")).isEmpty();
    }

    @Test
    void convert_skipsNullTagsAndTagsWithoutIdReturnedByRepository() {
        Tag valid = tag(2L, "два");
        Tag withoutId = new Tag();
        withoutId.setName("без id");

        when(tagRepository.findAllById(anyIterable()))
                .thenReturn(Arrays.asList(null, withoutId, valid));

        List<Tag> result = converter.convert("1, 2, 3");

        assertThat(result).containsExactly(valid);
    }

    private Tag tag(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }
}
