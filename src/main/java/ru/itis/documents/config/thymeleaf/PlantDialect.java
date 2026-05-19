package ru.itis.documents.config.thymeleaf;

import org.springframework.stereotype.Component;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.processor.IProcessor;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class PlantDialect extends AbstractProcessorDialect {

    public PlantDialect() {
        super("Plant Dialect", "plant", 1000);
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> processors = new LinkedHashSet<>();
        processors.add(new CapricBadgeTagProcessor(dialectPrefix));
        return processors;
    }
}