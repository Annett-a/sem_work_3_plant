package ru.itis.documents.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import ru.itis.documents.domain.entity.Tag;
import ru.itis.documents.repository.TagRepository;

import java.util.*;

@Component
@RequiredArgsConstructor
public class TagIdListToTagListConverter implements Converter<String, List<Tag>> {

    private final TagRepository tagRepository;

    @Override
    public List<Tag> convert(String source) {
        if (source == null) return List.of();
        String s = source.trim();
        if (s.isEmpty()) return List.of();

        Set<Long> ids = new LinkedHashSet<>();

        for (String part : s.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            try {
                long id = Long.parseLong(p);
                if (id > 0) ids.add(id);
            } catch (NumberFormatException ignored) {
            }
        }

        if (ids.isEmpty()) return List.of();

        List<Tag> found = tagRepository.findAllById(ids);
        if (found.isEmpty()) return List.of();

        Map<Long, Tag> byId = new LinkedHashMap<>();
        for (Tag t : found) {
            if (t != null && t.getId() != null) {
                byId.put(t.getId(), t);
            }
        }

        List<Tag> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Tag t = byId.get(id);
            if (t != null) ordered.add(t);
        }
        return ordered;
    }
}