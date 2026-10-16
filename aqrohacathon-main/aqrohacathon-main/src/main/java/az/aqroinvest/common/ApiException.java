package az.aqroinvest.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Biznes qaydası pozulduqda atılan, HTTP statusu və maşın oxunaqlı kodu daşıyan istisna. */
@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    /** Formada hansı sahənin səhv olduğu (sahə adı -> mesaj); yoxdursa null. */
    private final java.util.Map<String, String> fieldErrors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, java.util.Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null || fieldErrors.isEmpty() ? null : java.util.Map.copyOf(fieldErrors);
    }

    /** Bir və ya bir neçə sahə üzrə validasiya xətası (400). */
    public static ApiException fields(String message, java.util.Map<String, String> fieldErrors) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, fieldErrors);
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " tapılmadı");
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
