package ru.itis.documents.service;

import org.springframework.stereotype.Service;
import ru.itis.documents.domain.entity.CareProfile;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.domain.entity.Tag;
import ru.itis.documents.dto.view.CapriciousnessView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CapriciousnessService {

    public CapriciousnessView evaluate(PlantSpecies species) {
        if (species == null) {
            return defaultMid("Нет данных о виде");
        }

        List<String> tags = (species.getTags() == null) ? List.of() : species.getTags().stream()
                .map(Tag::getName)
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .toList();

        CareProfile care = species.getCareProfile();
        return evaluate(tags, care);
    }

    public CapriciousnessView evaluate(List<String> tagsLower, CareProfile care) {
        int score = 50;
        List<String> reasons = new ArrayList<>();

        if (has(tagsLower, "капризное")) {
            score += 25;
            reasons.add("Тег: «капризное»");
        }
        if (has(tagsLower, "для новичков")) {
            score -= 20;
            reasons.add("Тег: «для новичков»");
        }
        if (has(tagsLower, "тропическое")) {
            score += 10;
            reasons.add("Тег: «тропическое» (обычно требует стабильных условий)");
        }
        if (has(tagsLower, "влаголюбивое")) {
            score += 10;
            reasons.add("Тег: «влаголюбивое» (нужен контроль влажности)");
        }
        if (has(tagsLower, "засухоустойчивое")) {
            score -= 5;
            reasons.add("Тег: «засухоустойчивое» (прощает пропуски полива)");
        }
        if (has(tagsLower, "теневыносливое")) {
            score -= 5;
            reasons.add("Тег: «теневыносливое» (менее чувствительно к свету)");
        }
        if (has(tagsLower, "яркий свет")) {
            score += 5;
            reasons.add("Тег: «яркий свет» (важно правильно поставить растение)");
        }
        if (has(tagsLower, "цветущее")) {
            score += 5;
            reasons.add("Тег: «цветущее» (обычно требовательнее к уходу)");
        }
        if (has(tagsLower, "токсично для животных")) {
            score += 5;
            reasons.add("Тег: «токсично для животных» (нужна осторожность дома)");
        }

        if (care != null) {
            Integer water = care.getWaterIntervalDays();
            if (water != null) {
                if (water <= 3) {
                    score += 15;
                    reasons.add("Частый полив: каждые " + water + " дн.");
                } else if (water <= 7) {
                    score += 7;
                    reasons.add("Регулярный полив: каждые " + water + " дн.");
                } else if (water >= 14) {
                    score -= 5;
                    reasons.add("Редкий полив: раз в " + water + " дн.");
                }
            }

            Integer humidity = care.getHumidityPercent();
            if (humidity != null) {
                if (humidity >= 70) {
                    score += 10;
                    reasons.add("Нужна высокая влажность: " + humidity + "%");
                } else if (humidity <= 40) {
                    score += 3;
                    reasons.add("Чувствительно к сухому воздуху (" + humidity + "%)");
                }
            }

            String light = care.getLightLevel();
            if (light != null && !light.isBlank()) {
                String l = light.toLowerCase(Locale.ROOT);
                if (l.contains("прям") || l.contains("ярк")) {
                    score += 5;
                    reasons.add("Требования к свету: " + light);
                }
            }

            String notes = care.getNotes();
            if (notes != null && !notes.isBlank()) {
                String n = notes.toLowerCase(Locale.ROOT);
                if (n.contains("опрыск") || n.contains("увлаж")) {
                    score += 5;
                    reasons.add("Есть дополнительные условия в заметках по уходу");
                }
            }
        }

        score = clamp(score, 0, 100);

        String key;
        String label;
        if (score >= 70) {
            key = "HIGH";
            label = "Высокая";
        } else if (score <= 30) {
            key = "LOW";
            label = "Низкая";
        } else {
            key = "MID";
            label = "Средняя";
        }

        if (reasons.isEmpty()) {
            reasons.add("Средние требования без особых условий");
        }

        return new CapriciousnessView(key, label, null, score, reasons);
    }

    private static boolean has(List<String> tagsLower, String tagLower) {
        if (tagsLower == null || tagsLower.isEmpty()) return false;
        return tagsLower.stream().anyMatch(t -> t.equals(tagLower));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static CapriciousnessView defaultMid(String reason) {
        return new CapriciousnessView("MID", "Средняя", null, 50, List.of(reason));
    }
}