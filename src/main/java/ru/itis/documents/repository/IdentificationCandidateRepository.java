package ru.itis.documents.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.documents.domain.entity.IdentificationCandidate;

import java.util.List;

public interface IdentificationCandidateRepository extends JpaRepository<IdentificationCandidate, Long> {

    List<IdentificationCandidate> findAllByIdentification_IdOrderByScoreDesc(Long identificationId);
}