package ru.itis.documents.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final GridFsTemplate gridFsTemplate;

    public FileStorageService(GridFsTemplate gridFsTemplate) {
        this.gridFsTemplate = gridFsTemplate;
    }

    public String saveUserUpload(Long userId, MultipartFile file) {
        validateImage(file);

        String ext = guessExtension(file.getOriginalFilename(), file.getContentType());
        String storedName = UUID.randomUUID() + ext;

        Document metadata = new Document();
        metadata.put("userId", userId);
        metadata.put("originalName", file.getOriginalFilename());
        metadata.put("contentType", file.getContentType());

        try (InputStream in = file.getInputStream()) {
            ObjectId objectId = gridFsTemplate.store(in, storedName, file.getContentType(), metadata);
            return objectId.toHexString();
        } catch (Exception e) {
            log.error("Cannot save uploaded file to GridFS: userId={}, originalName={}",
                    userId, file.getOriginalFilename(), e);
            throw new IllegalStateException("Cannot save file", e);
        }
    }

    public Resource load(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Некорректный идентификатор файла");
        }

        final ObjectId objectId;
        try {
            objectId = new ObjectId(storageKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Некорректный идентификатор файла", e);
        }

        try {
            GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(objectId)));
            if (file == null) {
                throw new IllegalArgumentException("Файл не найден");
            }

            GridFsResource resource = gridFsTemplate.getResource(file);
            if (resource == null) {
                throw new IllegalArgumentException("Файл не найден");
            }

            return resource;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to load file from GridFS: storageKey={}", storageKey, e);
            throw new IllegalArgumentException("Не удалось прочитать файл", e);
        }
    }

    private static void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не выбран");
        }

        long max = 10L * 1024L * 1024L;
        if (file.getSize() > max) {
            throw new IllegalArgumentException("Файл слишком большой (до 10 МБ)");
        }

        String ct = file.getContentType();
        if (ct == null) {
            throw new IllegalArgumentException("Нужен файл-изображение");
        }

        String lower = ct.toLowerCase(Locale.ROOT);
        boolean typeOk = lower.equals("image/jpeg")
                || lower.equals("image/jpg")
                || lower.equals("image/pjpeg")
                || lower.equals("image/png")
                || lower.equals("image/webp");

        if (!typeOk) {
            throw new IllegalArgumentException("Нужен JPEG/PNG/WebP");
        }

        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(16);
            if (!looksLikeJpeg(head) && !looksLikePng(head) && !looksLikeWebp(head)) {
                throw new IllegalArgumentException("Файл не похож на изображение (JPEG/PNG/WebP)");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to read uploaded image for signature validation", e);
            throw new IllegalArgumentException("Не удалось прочитать файл", e);
        }
    }

    private static boolean looksLikeJpeg(byte[] head) {
        return head != null && head.length >= 2
                && (head[0] & 0xFF) == 0xFF
                && (head[1] & 0xFF) == 0xD8;
    }

    private static boolean looksLikePng(byte[] head) {
        return head != null && head.length >= 8
                && (head[0] & 0xFF) == 0x89
                && head[1] == 0x50
                && head[2] == 0x4E
                && head[3] == 0x47
                && head[4] == 0x0D
                && head[5] == 0x0A
                && head[6] == 0x1A
                && head[7] == 0x0A;
    }

    private static boolean looksLikeWebp(byte[] head) {
        return head != null && head.length >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
    }

    private static String guessExtension(String name, String contentType) {
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png")) return ".png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return ".jpg";
            if (lower.endsWith(".webp")) return ".webp";
        }

        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.equals("image/png")) return ".png";
            if (ct.equals("image/webp")) return ".webp";
        }

        return ".jpg";
    }

    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }

        final ObjectId objectId;
        try {
            objectId = new ObjectId(storageKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Некорректный идентификатор файла", e);
        }

        try {
            gridFsTemplate.delete(Query.query(Criteria.where("_id").is(objectId)));
        } catch (Exception e) {
            log.warn("Failed to delete file from GridFS: storageKey={}", storageKey, e);
            throw new IllegalStateException("Cannot delete file", e);
        }
    }
}