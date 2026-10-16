package az.aqroinvest.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.MediaType;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), req, ex.getFieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Məlumatlar düzgün doldurulmayıb", req, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Məlumatlar düzgün doldurulmayıb", req, errors);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<ApiError> handleDenied(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bu əməliyyat üçün icazəniz yoxdur", req, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleSize(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Fayl həcmi 10 MB-dan çox ola bilməz", req, null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "Məlumat başqa istifadəçi tərəfindən dəyişdirilib. Səhifəni yeniləyin.", req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT", "Məlumat bütövlüyü pozulur (təkrarlanan və ya əlaqəli qeyd)", req, null);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> handleBadArg(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Yanlış parametr: " + ex.getMessage(), req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), req, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "Tələb olunan parametr göndərilməyib: " + ex.getParameterName(), req,
                Map.of(ex.getParameterName(), "Doldurulmalıdır"));
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    ResponseEntity<ApiError> handleBinding(ServletRequestBindingException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), req, null);
    }

    /** Oxunmayan JSON və ya naməlum siyahı dəyəri (məs. useCase "XXX"): 500 deyil, izahlı 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        if (ex.getCause() instanceof InvalidFormatException f && f.getTargetType() != null && f.getTargetType().isEnum()) {
            String field = f.getPath().isEmpty() ? "?" : f.getPath().get(f.getPath().size() - 1).getFieldName();
            String allowed = Arrays.stream(f.getTargetType().getEnumConstants()).map(Object::toString).collect(Collectors.joining(", "));
            return build(HttpStatus.BAD_REQUEST, "UNKNOWN_VALUE", "Naməlum dəyər \"" + f.getValue() + "\" (" + field + "). Mümkün dəyərlər: " + allowed, req,
                    Map.of(field == null ? "?" : field, "Mümkün dəyərlər: " + allowed));
        }
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Sorğunun gövdəsi oxunmadı: JSON formatını yoxlayın", req, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Content-Type: application/json göndərin", req, null);
    }

    /** İstifadəçi canlı axını (SSE) bağlayıb: xəta deyil, cavab yazılmır. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void handleClientGone(AsyncRequestNotUsableException ex) {
        log.debug("Müştəri bağlantını bağladı: {}", ex.getMessage());
    }

    /** API üçün JSON 404, sayt səhifələri üçün dizaynlı "Səhifə tapılmadı" səhifəsi. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        String uri = req.getRequestURI();
        boolean api = uri.startsWith("/api/") || uri.startsWith("/mock/") || uri.startsWith("/v3/");
        String accept = req.getHeader("Accept");
        if (!api && (accept == null || accept.contains("text/html") || accept.contains("*/*")) && !uri.matches(".*\\.(js|css|json|svg|png|jpe?g|webp|ico|map|woff2?)$")) {
            String html = NOT_FOUND_HTML.get();
            if (html != null) return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(new MediaType("text", "html", StandardCharsets.UTF_8)).body(html);
        }
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resurs tapılmadı", req, null);
    }

    private static final java.util.function.Supplier<String> NOT_FOUND_HTML = () -> {
        try (var in = new ClassPathResource("static/404.html").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    };

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Daxili xəta baş verdi", req, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest req, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), code, message, req.getRequestURI(), fieldErrors));
    }
}
