-- V3__add_indexes.sql
-- Простые индексы под частые выборки (без pg_trgm)

-- Каталог: иногда ищем/сортируем по названию
CREATE INDEX IF NOT EXISTS idx_species_name
    ON plant_species (name);

-- Задачи: типовой сценарий "по статусу + ближайшие"
CREATE INDEX IF NOT EXISTS idx_care_tasks_status_due_date
    ON care_tasks (status, due_date);

-- История событий: по растению + последние сверху
CREATE INDEX IF NOT EXISTS idx_care_events_user_plant_time
    ON care_events (user_plant_id, event_time DESC);

-- Фото: последние фото растения
CREATE INDEX IF NOT EXISTS idx_photos_user_plant_uploaded_at
    ON photos (user_plant_id, uploaded_at DESC);

-- Индексы для таблиц связей (M2M)
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id
    ON user_roles (role_id);

CREATE INDEX IF NOT EXISTS idx_species_tags_tag_id
    ON plant_species_tags (tag_id);