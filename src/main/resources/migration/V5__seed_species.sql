-- V5__seed_species.sql
-- Seed-данные для каталога видов (локальная БД)
-- Схема: plant_species + care_profiles (care_profiles.species_id UNIQUE) + plant_species_tags

-- Доп. теги (часть уже есть в V2, конфликты игнорируем)
INSERT INTO tags (name)
VALUES
    ('неприхотливое'),
    ('сухолюбивое'),
    ('домашнее'),
    ('офис'),
    ('для новичков'),
    ('яркий свет'),
    ('полутень'),
    ('влаголюбивое')
    ON CONFLICT (name) DO NOTHING;

WITH
    s1 AS (
INSERT INTO plant_species (name, latin_name, description)
VALUES ('Сансевиерия', 'Sansevieria trifasciata',
    'Неприхотливое комнатное растение, хорошо переносит засуху.')
    RETURNING id
    ),
    s2 AS (
INSERT INTO plant_species (name, latin_name, description)
VALUES ('Спатифиллум', 'Spathiphyllum wallisii',
    'Любит регулярный полив и более влажный воздух, может цвести белыми соцветиями.')
    RETURNING id
    ),
    s3 AS (
INSERT INTO plant_species (name, latin_name, description)
VALUES ('Фикус Бенджамина', 'Ficus benjamina',
    'Декоративное растение для дома и офиса, предпочитает стабильные условия.')
    RETURNING id
    ),
    s4 AS (
INSERT INTO plant_species (name, latin_name, description)
VALUES ('Замиокулькас', 'Zamioculcas zamiifolia',
    'Очень неприхотливое растение, выдерживает редкий полив.')
    RETURNING id
    ),

    cp AS (
INSERT INTO care_profiles (species_id, water_interval_days, light_level, humidity_percent, notes)
SELECT s1.id, 14, 'яркий свет', 35, 'Не любит перелив. Полив после просушки.' FROM s1
UNION ALL
SELECT s2.id, 3,  'полутень',   60, 'Любит влажность и регулярный полив.'     FROM s2
UNION ALL
SELECT s3.id, 7,  'полутень',   45, 'Не любит резкие перестановки.'          FROM s3
UNION ALL
SELECT s4.id, 14, 'яркий свет', 35, 'Лучше недолить, чем перелить.'          FROM s4
    RETURNING 1
    ),

    links AS (
INSERT INTO plant_species_tags (species_id, tag_id)

-- Сансевиерия
SELECT s1.id, t.id FROM s1 JOIN tags t ON t.name IN
    ('для новичков', 'засухоустойчивое', 'неприхотливое', 'яркий свет', 'очищает воздух', 'нецветущее')

UNION ALL
-- Спатифиллум
SELECT s2.id, t.id FROM s2 JOIN tags t ON t.name IN
    ('влаголюбивое', 'полутень', 'цветущее', 'капризное', 'очищает воздух', 'домашнее')

UNION ALL
-- Фикус
SELECT s3.id, t.id FROM s3 JOIN tags t ON t.name IN
    ('полутень', 'офис', 'нецветущее')

UNION ALL
-- Замиокулькас
SELECT s4.id, t.id FROM s4 JOIN tags t ON t.name IN
    ('для новичков', 'засухоустойчивое', 'неприхотливое', 'яркий свет', 'офис', 'нецветущее')

    RETURNING 1
    )
SELECT 1;