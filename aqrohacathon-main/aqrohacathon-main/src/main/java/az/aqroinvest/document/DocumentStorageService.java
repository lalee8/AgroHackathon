package az.aqroinvest.document;

import az.aqroinvest.common.ApiException;
import az.aqroinvest.config.AppProperties;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Sənədlərin təhlükəsiz saxlanması:
 *  - məzmun növü faylın "sehrli baytları" ilə yoxlanılır (uzantıya etibar edilmir);
 *  - fayl təsadüfi UUID adı ilə veb-kökdən kənarda saxlanılır (path traversal mümkün deyil);
 *  - SHA-256 hash ilə bütövlük qeydə alınır.
 */
@Service
public class DocumentStorageService {
    public static final long MAX_SIZE = 10 * 1024 * 1024;
    private final Path root;

    public DocumentStorageService(AppProperties props) throws IOException {
        this.root = Path.of(props.storage().uploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "Fayl seçilməyib və ya boşdur");
        if (file.getSize() > MAX_SIZE) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Fayl həcmi 10 MB-dan çox ola bilməz");
        }
        try {
            return store(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new IllegalStateException("Fayl oxuna bilmədi", e);
        }
    }

    public StoredFile store(byte[] bytes, String originalName) {
        String type = detectType(bytes);
        if (type == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_TYPE",
                    "Yalnız PDF, PNG və JPG formatlı fayllar qəbul olunur");
        }
        String ext = switch (type) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> ".jpg";
        };
        String stored = UUID.randomUUID() + ext;
        try {
            Files.write(root.resolve(stored), bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new IllegalStateException("Fayl yadda saxlanıla bilmədi", e);
        }
        return new StoredFile(stored, sanitize(originalName, ext), type, bytes.length, sha256(bytes));
    }

    public Resource load(String storedName) {
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root) || !Files.exists(p)) throw ApiException.notFound("Fayl");
        return new PathResource(p);
    }

    public void delete(String storedName) {
        try {
            Path p = root.resolve(storedName).normalize();
            if (p.startsWith(root)) Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    static String detectType(byte[] b) {
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "application/pdf";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        return null;
    }

    static String sanitize(String name, String ext) {
        if (name == null || name.isBlank()) return "sened" + ext;
        String base = Path.of(name.replace("\\", "/")).getFileName().toString();
        base = base.replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (base.length() > 120) base = base.substring(0, 120);
        return base.isEmpty() ? "sened" + ext : base;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record StoredFile(String storedName, String originalName, String contentType, long size, String sha256) {}
}
