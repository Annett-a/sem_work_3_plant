package ru.itis.documents.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileStorageServiceTest {

    GridFsTemplate gridFsTemplate;
    FileStorageService service;

    @BeforeEach
    void setUp() {
        gridFsTemplate = mock(GridFsTemplate.class);
        service = new FileStorageService(gridFsTemplate);
    }

    @Test
    void saveUserUpload_savesJpegImageAndReturnsObjectId() {
        MockMultipartFile file = new MockMultipartFile(
                "photo", "plant.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3}
        );
        ObjectId objectId = new ObjectId();

        when(gridFsTemplate.store(any(), anyString(), eq("image/jpeg"), any(Document.class)))
                .thenReturn(objectId);

        String storageKey = service.saveUserUpload(7L, file);

        assertThat(storageKey).isEqualTo(objectId.toHexString());
    }

    @Test
    void saveUserUpload_usesPngAndWebpExtensionsWhenPossible() {
        MockMultipartFile png = new MockMultipartFile(
                "photo", "a.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        );
        MockMultipartFile webp = new MockMultipartFile(
                "photo", "a.bin", "image/webp",
                new byte[]{'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'}
        );

        when(gridFsTemplate.store(any(), anyString(), anyString(), any(Document.class)))
                .thenReturn(new ObjectId(), new ObjectId());

        service.saveUserUpload(1L, png);
        service.saveUserUpload(1L, webp);

        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        verify(gridFsTemplate, times(2)).store(any(), names.capture(), anyString(), any(Document.class));

        assertThat(names.getAllValues().get(0)).endsWith(".png");
        assertThat(names.getAllValues().get(1)).endsWith(".webp");
    }

    @Test
    void saveUserUpload_throwsForNullOrEmptyFile() {
        assertThatThrownBy(() -> service.saveUserUpload(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не выбран");

        assertThatThrownBy(() -> service.saveUserUpload(1L, new MockMultipartFile("photo", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не выбран");
    }

    @Test
    void saveUserUpload_throwsForTooLargeOrUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "photo", "a.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8}
        );
        MockMultipartFile spy = org.mockito.Mockito.spy(file);
        when(spy.getSize()).thenReturn(10L * 1024L * 1024L + 1L);

        assertThatThrownBy(() -> service.saveUserUpload(1L, spy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("слишком большой");

        MockMultipartFile gif = new MockMultipartFile("photo", "a.gif", "image/gif", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.saveUserUpload(1L, gif))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPEG/PNG/WebP");
    }

    @Test
    void saveUserUpload_throwsForWrongSignatureAndUnreadableStream() throws IOException {
        MockMultipartFile badSignature = new MockMultipartFile(
                "photo", "a.jpg", "image/jpeg", new byte[]{1, 2, 3, 4}
        );
        assertThatThrownBy(() -> service.saveUserUpload(1L, badSignature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не похож");

        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getSize()).thenReturn(10L);
        when(broken.getContentType()).thenReturn("image/jpeg");
        when(broken.getInputStream()).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> service.saveUserUpload(1L, broken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("прочитать файл");
    }

    @Test
    void saveUserUpload_wrapsFailureDuringStoreAfterValidation() throws Exception {
        MultipartFile file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(3L);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn("plant.jpg");
        when(file.getInputStream())
                .thenReturn(new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xD8, 1}))
                .thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> service.saveUserUpload(1L, file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot save file");
    }

    @Test
    void saveUserUpload_throwsWhenContentTypeIsNull() {
        MockMultipartFile file = new MockMultipartFile(
                "photo", "plant.jpg", null, new byte[]{(byte) 0xFF, (byte) 0xD8, 1}
        );

        assertThatThrownBy(() -> service.saveUserUpload(1L, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("изображение");
    }

    @Test
    void saveUserUpload_acceptsJpgAndPjpegContentTypes() {
        MockMultipartFile jpg = new MockMultipartFile(
                "photo", null, "image/jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1}
        );
        MockMultipartFile pjpeg = new MockMultipartFile(
                "photo", null, "image/pjpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 2}
        );

        when(gridFsTemplate.store(any(), anyString(), anyString(), any(Document.class)))
                .thenReturn(new ObjectId(), new ObjectId());

        String jpgId = service.saveUserUpload(1L, jpg);
        String pjpegId = service.saveUserUpload(1L, pjpeg);

        assertThat(jpgId).hasSize(24);
        assertThat(pjpegId).hasSize(24);
    }

    @Test
    void load_returnsResourceWhenFileExists() {
        ObjectId objectId = new ObjectId();
        GridFSFile gridFsFile = mock(GridFSFile.class);
        GridFsResource resource = mock(GridFsResource.class);

        when(gridFsTemplate.findOne(any())).thenReturn(gridFsFile);
        when(gridFsTemplate.getResource(gridFsFile)).thenReturn(resource);

        assertThat(service.load(objectId.toHexString())).isSameAs(resource);
    }

    @Test
    void load_rejectsBlankAndInvalidId() {
        assertThatThrownBy(() -> service.load(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("идентификатор");

        assertThatThrownBy(() -> service.load("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("идентификатор");

        assertThatThrownBy(() -> service.load("bad-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("идентификатор");
    }

    @Test
    void load_throwsWhenFileMissing() {
        ObjectId objectId = new ObjectId();
        when(gridFsTemplate.findOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.load(objectId.toHexString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Файл не найден");
    }

    @Test
    void load_throwsWhenResourceIsNull() {
        ObjectId objectId = new ObjectId();
        GridFSFile gridFsFile = mock(GridFSFile.class);

        when(gridFsTemplate.findOne(any())).thenReturn(gridFsFile);
        when(gridFsTemplate.getResource(gridFsFile)).thenReturn(null);

        assertThatThrownBy(() -> service.load(objectId.toHexString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Файл не найден");
    }

    @Test
    void load_wrapsUnexpectedFailure() {
        ObjectId objectId = new ObjectId();
        when(gridFsTemplate.findOne(any())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.load(objectId.toHexString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("прочитать файл");
    }

    @Test
    void delete_ignoresBlankOrNull() {
        service.delete(null);
        service.delete("   ");

        verifyNoInteractions(gridFsTemplate);
    }

    @Test
    void delete_deletesByObjectId() {
        ObjectId objectId = new ObjectId();

        service.delete(objectId.toHexString());

        verify(gridFsTemplate).delete(any());
    }

    @Test
    void delete_rejectsInvalidId() {
        assertThatThrownBy(() -> service.delete("bad-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("идентификатор");
    }

    @Test
    void delete_wrapsUnexpectedFailure() {
        ObjectId objectId = new ObjectId();
        doThrow(new RuntimeException("boom")).when(gridFsTemplate).delete(any());

        assertThatThrownBy(() -> service.delete(objectId.toHexString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete file");
    }

    @Test
    void privateSignatureHelpers_coverTrueAndFalseBranches() throws Exception {
        Method jpeg = FileStorageService.class.getDeclaredMethod("looksLikeJpeg", byte[].class);
        Method png = FileStorageService.class.getDeclaredMethod("looksLikePng", byte[].class);
        Method webp = FileStorageService.class.getDeclaredMethod("looksLikeWebp", byte[].class);
        jpeg.setAccessible(true);
        png.setAccessible(true);
        webp.setAccessible(true);

        assertThat((boolean) jpeg.invoke(null, new Object[]{null})).isFalse();
        assertThat((boolean) jpeg.invoke(null, new byte[]{1})).isFalse();
        assertThat((boolean) jpeg.invoke(null, new byte[]{0, (byte) 0xD8})).isFalse();
        assertThat((boolean) jpeg.invoke(null, new byte[]{(byte) 0xFF, 0})).isFalse();
        assertThat((boolean) jpeg.invoke(null, new byte[]{(byte) 0xFF, (byte) 0xD8})).isTrue();

        byte[] validPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat((boolean) png.invoke(null, new Object[]{null})).isFalse();
        assertThat((boolean) png.invoke(null, new byte[]{1, 2, 3})).isFalse();
        for (int i = 0; i < validPng.length; i++) {
            byte[] invalid = validPng.clone();
            invalid[i] = (byte) (invalid[i] + 1);
            assertThat((boolean) png.invoke(null, invalid)).isFalse();
        }
        assertThat((boolean) png.invoke(null, validPng)).isTrue();

        byte[] validWebp = new byte[]{'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'};
        assertThat((boolean) webp.invoke(null, new Object[]{null})).isFalse();
        assertThat((boolean) webp.invoke(null, new byte[]{1, 2, 3})).isFalse();

        byte[] invalid0 = validWebp.clone();
        invalid0[0] = 'X';
        assertThat((boolean) webp.invoke(null, invalid0)).isFalse();

        byte[] invalid1 = validWebp.clone();
        invalid1[1] = 'X';
        assertThat((boolean) webp.invoke(null, invalid1)).isFalse();

        byte[] invalid2 = validWebp.clone();
        invalid2[2] = 'X';
        assertThat((boolean) webp.invoke(null, invalid2)).isFalse();

        byte[] invalid3 = validWebp.clone();
        invalid3[3] = 'X';
        assertThat((boolean) webp.invoke(null, invalid3)).isFalse();

        byte[] invalid8 = validWebp.clone();
        invalid8[8] = 'X';
        assertThat((boolean) webp.invoke(null, invalid8)).isFalse();

        byte[] invalid9 = validWebp.clone();
        invalid9[9] = 'X';
        assertThat((boolean) webp.invoke(null, invalid9)).isFalse();

        byte[] invalid10 = validWebp.clone();
        invalid10[10] = 'X';
        assertThat((boolean) webp.invoke(null, invalid10)).isFalse();

        byte[] invalid11 = validWebp.clone();
        invalid11[11] = 'X';
        assertThat((boolean) webp.invoke(null, invalid11)).isFalse();

        assertThat((boolean) webp.invoke(null, validWebp)).isTrue();
    }

    @Test
    void privateGuessExtension_coversBranches() throws Exception {
        Method guess = FileStorageService.class.getDeclaredMethod("guessExtension", String.class, String.class);
        guess.setAccessible(true);

        assertThat((String) guess.invoke(null, "a.png", "image/jpeg")).isEqualTo(".png");
        assertThat((String) guess.invoke(null, "a.jpg", "image/png")).isEqualTo(".jpg");
        assertThat((String) guess.invoke(null, "a.jpeg", "image/png")).isEqualTo(".jpg");
        assertThat((String) guess.invoke(null, "a.webp", "image/jpeg")).isEqualTo(".webp");
        assertThat((String) guess.invoke(null, "a.bin", "image/png")).isEqualTo(".png");
        assertThat((String) guess.invoke(null, "a.bin", "image/webp")).isEqualTo(".webp");
        assertThat((String) guess.invoke(null, "a.bin", "image/jpeg")).isEqualTo(".jpg");
        assertThat((String) guess.invoke(null, null, null)).isEqualTo(".jpg");
    }
}