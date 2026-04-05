-- V6__plantnet_cache.sql
-- 4.5.4 (P1): кэш распознаваний по фото + сохранение остатка квоты Pl@ntNet

ALTER TABLE plant_identifications
    ADD COLUMN IF NOT EXISTS photo_hash VARCHAR(64);

ALTER TABLE plant_identifications
    ADD COLUMN IF NOT EXISTS plantnet_remaining_requests INTEGER;

CREATE INDEX IF NOT EXISTS idx_pi_photo_hash ON plant_identifications(photo_hash);
CREATE INDEX IF NOT EXISTS idx_pi_photo_hash_status ON plant_identifications(photo_hash, status);