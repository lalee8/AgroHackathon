package az.aqroinvest.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** Bütün API xətaları üçün vahid cavab formatı (EKTİS və digər sistemlər üçün proqnozlaşdırıla bilən). */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(Instant timestamp, int status, String code, String message, String path,
                       Map<String, String> fieldErrors) {
}
