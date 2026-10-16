package az.aqroinvest;

import az.aqroinvest.application.ApplicationStatus;
import az.aqroinvest.common.Masking;
import az.aqroinvest.plot.GeoUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class UnitTests {

    @Test
    @DisplayName("Status avtomatı yalnız icazəli keçidlərə imkan verir")
    void statusMachine() {
        assertThat(ApplicationStatus.SUBMITTED.canAdminMoveTo(ApplicationStatus.UNDER_REVIEW)).isTrue();
        assertThat(ApplicationStatus.SUBMITTED.canAdminMoveTo(ApplicationStatus.DECIDED)).isFalse();
        assertThat(ApplicationStatus.UNDER_REVIEW.canAdminMoveTo(ApplicationStatus.DECIDED)).isTrue();
        assertThat(ApplicationStatus.DECIDED.adminTransitions()).isEmpty();
        assertThat(ApplicationStatus.DRAFT.isEditableByApplicant()).isTrue();
        assertThat(ApplicationStatus.UNDER_REVIEW.isEditableByApplicant()).isFalse();
    }

    @Test
    @DisplayName("Şəxsi məlumatların maskalanması")
    void masking() {
        assertThat(Masking.fin("5ZK8M2P")).isEqualTo("5Z****P");
        assertThat(Masking.email("kamran@example.az")).isEqualTo("ka***@example.az");
        assertThat(Masking.phone("+994503214567")).isEqualTo("+994503***567");
    }

    @Test
    @DisplayName("Poliqon qapalıdır və sahənin mərkəzi ətrafında qurulur")
    void polygon() {
        String p = GeoUtil.polygonFor(39.6, 47.1, BigDecimal.valueOf(100), 42);
        assertThat(p).startsWith("[[").endsWith("]]");
        String first = p.substring(1, p.indexOf(']') + 1);
        assertThat(p).endsWith(first + "]");
    }
}
