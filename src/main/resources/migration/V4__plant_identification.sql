-- V4__plant_identification.sql
-- История распознаваний Pl@ntNet + кандидаты
-- Добавлено, чтобы ddl-auto: validate проходил на пустой БД

CREATE TABLE plant_identifications (
                                       id                       BIGSERIAL PRIMARY KEY,
                                       user_id                  BIGINT NOT NULL,
                                       created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                       status                   VARCHAR(32) NOT NULL,
                                       source_photo_path        VARCHAR(512) NOT NULL,
                                       selected_scientific_name VARCHAR(255),
                                       best_match               VARCHAR(255),
                                       best_match_score         DOUBLE PRECISION,
                                       raw_response_json        TEXT,
                                       error_message            TEXT,
                                       CONSTRAINT fk_plant_identifications_user
                                           FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                       CONSTRAINT chk_plant_identification_status
                                           CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

-- Частый сценарий: история пользователя (order by created_at desc)
CREATE INDEX idx_pi_user_created_at ON plant_identifications(user_id, created_at DESC);
CREATE INDEX idx_pi_status ON plant_identifications(status);

CREATE TABLE identification_candidates (
                                           id                BIGSERIAL PRIMARY KEY,
                                           identification_id BIGINT NOT NULL,
                                           scientific_name   VARCHAR(255) NOT NULL,
                                           common_names      VARCHAR(1000),
                                           score             DOUBLE PRECISION NOT NULL,
                                           CONSTRAINT fk_identification_candidates_identification
                                               FOREIGN KEY (identification_id) REFERENCES plant_identifications(id) ON DELETE CASCADE
);

CREATE INDEX idx_ic_identification_id ON identification_candidates(identification_id);