-- Этап 3.4: реальное фото вида (а не заглушка).
-- Храним URL изображения, который приходит из Perenual (default_image.original_url).

ALTER TABLE plant_species
    ADD COLUMN IF NOT EXISTS image_url TEXT;