package ru.itis.documents.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import ru.itis.documents.domain.enums.LightLevel;

@Component
public class LightLevelConverter implements Converter<String, LightLevel> {

    @Override
    public LightLevel convert(String source) {
        return LightLevel.from(source);
    }
}