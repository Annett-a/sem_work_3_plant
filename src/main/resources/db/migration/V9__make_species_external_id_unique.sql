DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1
                       FROM pg_constraint
                       WHERE conname = 'uq_plant_species_external_id') THEN
            ALTER TABLE plant_species
                ADD CONSTRAINT uq_plant_species_external_id UNIQUE (external_id);
        END IF;
    END
$$;