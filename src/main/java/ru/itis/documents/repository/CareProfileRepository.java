package ru.itis.documents.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.documents.domain.entity.CareProfile;

public interface CareProfileRepository extends JpaRepository<CareProfile, Long> {
}