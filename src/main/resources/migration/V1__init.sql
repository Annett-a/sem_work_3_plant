-- V1__init.sql
-- Семестровая 3: Plants / Care Tracker
-- PostgreSQL

-- =========================
-- 1) Базовые справочники
-- =========================

CREATE TABLE users (
                       id            BIGSERIAL PRIMARY KEY,
                       email         VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       enabled       BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                       updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
                       id         BIGSERIAL PRIMARY KEY,
                       name       VARCHAR(50) NOT NULL UNIQUE,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- M2M: users <-> roles
CREATE TABLE user_roles (
                            user_id BIGINT NOT NULL,
                            role_id BIGINT NOT NULL,
                            PRIMARY KEY (user_id, role_id),
                            CONSTRAINT fk_user_roles_user
                                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                            CONSTRAINT fk_user_roles_role
                                FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- =========================
-- 2) Каталог растений
-- =========================

CREATE TABLE plant_species (
                               id          BIGSERIAL PRIMARY KEY,
                               name        VARCHAR(200) NOT NULL,
                               latin_name  VARCHAR(200),
                               external_id BIGINT,
                               description TEXT,
                               created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_species_external_id ON plant_species(external_id);

-- O2O: plant_species -> care_profiles (1 вид = 1 профиль ухода)
CREATE TABLE care_profiles (
                               id                  BIGSERIAL PRIMARY KEY,
                               species_id          BIGINT NOT NULL UNIQUE,
                               water_interval_days INT CHECK (water_interval_days IS NULL OR water_interval_days > 0),
                               light_level         VARCHAR(30),
                               humidity_percent    INT CHECK (humidity_percent IS NULL OR (humidity_percent >= 0 AND humidity_percent <= 100)),
                               notes               TEXT,
                               CONSTRAINT fk_care_profiles_species
                                   FOREIGN KEY (species_id) REFERENCES plant_species(id) ON DELETE CASCADE
);

CREATE TABLE tags (
                      id   BIGSERIAL PRIMARY KEY,
                      name VARCHAR(80) NOT NULL UNIQUE
);

-- M2M: plant_species <-> tags
CREATE TABLE plant_species_tags (
                                    species_id BIGINT NOT NULL,
                                    tag_id     BIGINT NOT NULL,
                                    PRIMARY KEY (species_id, tag_id),
                                    CONSTRAINT fk_species_tags_species
                                        FOREIGN KEY (species_id) REFERENCES plant_species(id) ON DELETE CASCADE,
                                    CONSTRAINT fk_species_tags_tag
                                        FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

-- =========================
-- 3) Данные пользователя
-- =========================

CREATE TABLE rooms (
                       id         BIGSERIAL PRIMARY KEY,
                       user_id    BIGINT NOT NULL,
                       name       VARCHAR(120) NOT NULL,
                       light_level VARCHAR(30),
                       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                       CONSTRAINT fk_rooms_user
                           FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                       CONSTRAINT uq_rooms_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_rooms_user_id ON rooms(user_id);

CREATE TABLE user_plants (
                             id           BIGSERIAL PRIMARY KEY,
                             user_id      BIGINT NOT NULL,
                             species_id   BIGINT NOT NULL,
                             room_id      BIGINT,
                             nickname     VARCHAR(120) NOT NULL,
                             purchase_date DATE,
                             notes        TEXT,
                             created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                             updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                             CONSTRAINT fk_user_plants_user
                                 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                             CONSTRAINT fk_user_plants_species
                                 FOREIGN KEY (species_id) REFERENCES plant_species(id),
                             CONSTRAINT fk_user_plants_room
                                 FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE SET NULL
);

CREATE INDEX idx_user_plants_user_id ON user_plants(user_id);
CREATE INDEX idx_user_plants_species_id ON user_plants(species_id);
CREATE INDEX idx_user_plants_room_id ON user_plants(room_id);

-- =========================
-- 4) Уход: задачи, события, фото
-- =========================

CREATE TABLE care_tasks (
                            id            BIGSERIAL PRIMARY KEY,
                            user_plant_id BIGINT NOT NULL,
                            type          VARCHAR(32) NOT NULL,
                            status        VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
                            due_date      DATE,
                            note          TEXT,
                            created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                            completed_at  TIMESTAMPTZ,
                            CONSTRAINT fk_care_tasks_user_plant
                                FOREIGN KEY (user_plant_id) REFERENCES user_plants(id) ON DELETE CASCADE,
                            CONSTRAINT chk_care_task_type
                                CHECK (type IN ('WATER', 'FERTILIZE', 'REPOT', 'PRUNE', 'SPRAY')),
                            CONSTRAINT chk_care_task_status
                                CHECK (status IN ('PLANNED', 'DONE', 'CANCELED'))
);

CREATE INDEX idx_care_tasks_user_plant_id ON care_tasks(user_plant_id);
CREATE INDEX idx_care_tasks_due_date ON care_tasks(due_date);

CREATE TABLE care_events (
                             id            BIGSERIAL PRIMARY KEY,
                             user_plant_id BIGINT NOT NULL,
                             type          VARCHAR(32) NOT NULL,
                             event_time    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                             comment       TEXT,
                             CONSTRAINT fk_care_events_user_plant
                                 FOREIGN KEY (user_plant_id) REFERENCES user_plants(id) ON DELETE CASCADE,
                             CONSTRAINT chk_care_event_type
                                 CHECK (type IN ('WATER', 'FERTILIZE', 'REPOT', 'PRUNE', 'SPRAY'))
);

CREATE INDEX idx_care_events_user_plant_id ON care_events(user_plant_id);
CREATE INDEX idx_care_events_event_time ON care_events(event_time);

CREATE TABLE photos (
                        id            BIGSERIAL PRIMARY KEY,
                        user_plant_id BIGINT NOT NULL,
                        storage_key   VARCHAR(500) NOT NULL,
                        original_name VARCHAR(255),
                        content_type  VARCHAR(120),
                        uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        CONSTRAINT fk_photos_user_plant
                            FOREIGN KEY (user_plant_id) REFERENCES user_plants(id) ON DELETE CASCADE
);

CREATE INDEX idx_photos_user_plant_id ON photos(user_plant_id);