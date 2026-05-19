package ru.itis.documents.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ru.itis.documents.domain.entity.AppUser;
import ru.itis.documents.domain.entity.IdentificationCandidate;
import ru.itis.documents.domain.entity.PlantIdentification;
import ru.itis.documents.domain.enums.PlantIdentificationStatus;
import ru.itis.documents.exception.IntegrationException;
import ru.itis.documents.exception.PlantIdentificationNotFoundException;
import ru.itis.documents.integration.plantnet.PlantNetClient;
import ru.itis.documents.repository.AppUserRepository;
import ru.itis.documents.repository.PlantIdentificationRepository;

import java.time.Instant;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class PlantIdentificationService {

    private static final Logger log = LoggerFactory.getLogger(PlantIdentificationService.class);

    private final AppUserRepository appUserRepository;
    private final PlantIdentificationRepository identificationRepository;
    private final FileStorageService fileStorageService;
    private final PlantNetClient plantNetClient;
    private final ObjectMapper objectMapper;

    public PlantIdentificationService(
            AppUserRepository appUserRepository,
            PlantIdentificationRepository identificationRepository,
            FileStorageService fileStorageService,
            PlantNetClient plantNetClient,
            ObjectMapper objectMapper
    ) {
        this.appUserRepository = appUserRepository;
        this.identificationRepository = identificationRepository;
        this.fileStorageService = fileStorageService;
        this.plantNetClient = plantNetClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Long identify(String username, MultipartFile photo) {
        AppUser user = resolveUser(username);

        final byte[] bytes;
        try {
            bytes = photo.getBytes();
        } catch (Exception e) {
            log.error("Failed to read uploaded file for identification: username={}, originalFilename={}",
                    username,
                    photo != null ? photo.getOriginalFilename() : null,
                    e);
            throw new IllegalArgumentException("Не удалось прочитать файл", e);
        }

        final String hash = sha256Hex(bytes);

        var cachedOpt = identificationRepository
                .findFirstByUser_IdAndPhotoHashAndStatusOrderByCreatedAtDesc(
                        user.getId(),
                        hash,
                        PlantIdentificationStatus.COMPLETED
                );

        if (cachedOpt.isPresent()) {
            PlantIdentification cached = cachedOpt.get();
            log.info("PlantNet cache hit: userId={}, cachedId={}", user.getId(), cached.getId());

            String savedPath = fileStorageService.saveUserUpload(user.getId(), photo);

            PlantIdentification pi = new PlantIdentification();
            pi.setUser(user);
            pi.setCreatedAt(Instant.now());
            pi.setStatus(PlantIdentificationStatus.COMPLETED);
            pi.setSourcePhotoPath(savedPath);
            pi.setPhotoHash(hash);

            pi.setSelectedScientificName(cached.getSelectedScientificName());
            pi.setBestMatch(cached.getBestMatch());
            pi.setBestMatchScore(cached.getBestMatchScore());
            pi.setPlantnetRemainingRequests(cached.getPlantnetRemainingRequests());
            pi.setRawResponseJson(cached.getRawResponseJson());

            for (IdentificationCandidate old : cached.getCandidates()) {
                IdentificationCandidate ic = new IdentificationCandidate();
                ic.setScientificName(old.getScientificName());
                ic.setCommonNames(old.getCommonNames());
                ic.setScore(old.getScore());
                pi.addCandidate(ic);
            }

            pi = identificationRepository.save(pi);
            return pi.getId();
        }

        String savedPath = fileStorageService.saveUserUpload(user.getId(), photo);

        PlantIdentification pi = new PlantIdentification();
        pi.setUser(user);
        pi.setCreatedAt(Instant.now());
        pi.setStatus(PlantIdentificationStatus.PROCESSING);
        pi.setSourcePhotoPath(savedPath);
        pi.setPhotoHash(hash);

        pi = identificationRepository.save(pi);

        try {
            var res = plantNetClient.identify(bytes, photo.getOriginalFilename());

            pi.setStatus(PlantIdentificationStatus.COMPLETED);
            pi.setBestMatch(res.bestMatch());
            pi.setRawResponseJson(res.rawJsonString());
            pi.setPlantnetRemainingRequests(res.remainingIdentificationRequests());

            pi.clearCandidates();
            List<PlantNetClient.Candidate> candidates = res.candidates();
            for (var c : candidates) {
                IdentificationCandidate ic = new IdentificationCandidate();
                ic.setScientificName(c.scientificName());
                ic.setCommonNames(String.join(", ", c.commonNames()));
                ic.setScore(c.score());
                pi.addCandidate(ic);
            }

            if (!candidates.isEmpty()) {
                pi.setSelectedScientificName(candidates.get(0).scientificName());
                pi.setBestMatchScore(candidates.get(0).score());
            }

            identificationRepository.save(pi);
            return pi.getId();

        } catch (IntegrationException e) {
            log.warn("PlantNet integration failed (id={}, code={})", pi.getId(), e.getCode());
            pi.setStatus(PlantIdentificationStatus.FAILED);
            pi.setErrorMessage(e.getUserMessage());
            identificationRepository.save(pi);
            throw e;

        } catch (Exception e) {
            log.error("Plant identification failed (id={})", pi.getId(), e);
            pi.setStatus(PlantIdentificationStatus.FAILED);
            pi.setErrorMessage(e.getMessage());
            identificationRepository.save(pi);
            return pi.getId();
        }
    }

    @Transactional
    public Long identifyFromExistingPhoto(
            String username,
            String sourcePhotoPath,
            String originalFilename,
            byte[] bytes
    ) {
        AppUser user = resolveUser(username);

        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Пустой файл изображения");
        }

        final String hash = sha256Hex(bytes);

        var cachedOpt = identificationRepository
                .findFirstByUser_IdAndPhotoHashAndStatusOrderByCreatedAtDesc(
                        user.getId(), hash, PlantIdentificationStatus.COMPLETED
                );

        if (cachedOpt.isPresent()) {
            PlantIdentification cached = cachedOpt.get();
            log.info("PlantNet cache hit (existing photo): userId={}, cachedId={}", user.getId(), cached.getId());

            PlantIdentification pi = new PlantIdentification();
            pi.setUser(user);
            pi.setCreatedAt(Instant.now());
            pi.setStatus(PlantIdentificationStatus.COMPLETED);
            pi.setSourcePhotoPath(sourcePhotoPath);
            pi.setPhotoHash(hash);

            pi.setSelectedScientificName(cached.getSelectedScientificName());
            pi.setBestMatch(cached.getBestMatch());
            pi.setBestMatchScore(cached.getBestMatchScore());
            pi.setPlantnetRemainingRequests(cached.getPlantnetRemainingRequests());
            pi.setRawResponseJson(cached.getRawResponseJson());

            pi.clearCandidates();
            for (IdentificationCandidate old : cached.getCandidates()) {
                IdentificationCandidate ic = new IdentificationCandidate();
                ic.setScientificName(old.getScientificName());
                ic.setCommonNames(old.getCommonNames());
                ic.setScore(old.getScore());
                pi.addCandidate(ic);
            }

            pi = identificationRepository.save(pi);
            return pi.getId();
        }

        PlantIdentification pi = new PlantIdentification();
        pi.setUser(user);
        pi.setCreatedAt(Instant.now());
        pi.setStatus(PlantIdentificationStatus.PROCESSING);
        pi.setSourcePhotoPath(sourcePhotoPath);
        pi.setPhotoHash(hash);

        pi = identificationRepository.save(pi);

        try {
            var res = plantNetClient.identify(bytes, originalFilename);

            pi.setStatus(PlantIdentificationStatus.COMPLETED);
            pi.setBestMatch(res.bestMatch());
            pi.setRawResponseJson(res.rawJsonString());
            pi.setPlantnetRemainingRequests(res.remainingIdentificationRequests());

            pi.clearCandidates();
            List<PlantNetClient.Candidate> candidates = res.candidates();
            for (var c : candidates) {
                IdentificationCandidate ic = new IdentificationCandidate();
                ic.setScientificName(c.scientificName());
                ic.setCommonNames(String.join(", ", c.commonNames()));
                ic.setScore(c.score());
                pi.addCandidate(ic);
            }

            if (!candidates.isEmpty()) {
                pi.setSelectedScientificName(candidates.get(0).scientificName());
                pi.setBestMatchScore(candidates.get(0).score());
            }

            identificationRepository.save(pi);
            return pi.getId();

        } catch (IntegrationException e) {
            log.warn("PlantNet integration failed (existing photo) (id={}, code={})", pi.getId(), e.getCode());
            pi.setStatus(PlantIdentificationStatus.FAILED);
            pi.setErrorMessage(e.getUserMessage());
            identificationRepository.save(pi);
            throw e;

        } catch (Exception e) {
            log.error("Plant identification failed (existing photo) (id={})", pi.getId(), e);
            pi.setStatus(PlantIdentificationStatus.FAILED);
            pi.setErrorMessage(e.getMessage());
            identificationRepository.save(pi);
            return pi.getId();
        }
    }

    public List<PlantIdentification> history(String username) {
        AppUser user = resolveUser(username);
        return identificationRepository.findHistory(user.getId());
    }

    public PlantIdentification getDetails(String username, Long id) {
        AppUser user = resolveUser(username);
        return identificationRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new PlantIdentificationNotFoundException("Распознавание не найдено"));
    }

    @Transactional
    public void selectScientificName(String username, Long id, String selectedScientificName) {
        PlantIdentification pi = getDetails(username, id);
        pi.setSelectedScientificName(selectedScientificName);
        identificationRepository.save(pi);
    }

    public String photoPathFor(String username, Long id) {
        PlantIdentification pi = getDetails(username, id);
        return pi.getSourcePhotoPath();
    }

    private AppUser resolveUser(String username) {
        return appUserRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}