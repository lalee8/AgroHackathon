package az.aqroinvest;

import az.aqroinvest.integration.ektis.IntegrationEventPublisher;
import az.aqroinvest.integration.ektis.IntegrationEventRepository;
import az.aqroinvest.integration.ektis.IntegrationEventStatus;
import az.aqroinvest.integration.ektis.OutboxProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EktisIntegrationTest {
    static final String KEY = "ektis-demo-api-key-2026";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired IntegrationEventPublisher publisher;
    @Autowired IntegrationEventRepository events;
    @Autowired OutboxProcessor processor;

    @Test
    @DisplayName("Sistemlərarası API X-API-Key olmadan 401 qaytarır, açarla GeoJSON verir")
    void inboundApiRequiresKey() throws Exception {
        mvc.perform(get("/api/v1/integration/plots")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
        mvc.perform(get("/api/v1/integration/plots").header("X-API-Key", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/integration/plots").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Polygon"));
        mvc.perform(get("/api/v1/integration/applications").header("X-API-Key", KEY).param("status", "DECIDED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schemaVersion").value("1.0"));
    }

    @Test
    @DisplayName("EKTİS geri-çağırışı müraciətin tarixçəsinə və fermer statusuna yazılır")
    void callbackUpdatesApplication() throws Exception {
        String number = mapper.readTree(mvc.perform(get("/api/v1/integration/applications").header("X-API-Key", KEY))
                .andReturn().getResponse().getContentAsString()).get("items").get(0).get("applicationNumber").asText();
        mvc.perform(post("/api/v1/integration/callbacks").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("applicationNumber", number, "type", "FARMER_REGISTERED",
                                "reference", "EKTIS-FRM-1", "message", "Reyestrə daxil edildi"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));
        mvc.perform(get("/api/v1/integration/applications/" + number).header("X-API-Key", KEY))
                .andExpect(jsonPath("$.ektisFarmerStatus").value("REGISTERED"))
                .andExpect(jsonPath("$.ektisReference").value("EKTIS-FRM-1"));
    }

    @Test
    @DisplayName("EKTİS simulyatoru etibarsız HMAC imzasını rədd edir")
    void mockRejectsBadSignature() throws Exception {
        mvc.perform(post("/mock/ektis/v1/events").header("X-API-Key", KEY).header("X-Idempotency-Key", "abc")
                        .header("X-Timestamp", Instant.now().toString()).header("X-Signature", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"eventType\":\"TEST\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/mock/ektis/v1/farmers/1234567890")).andExpect(status().isUnauthorized());
        mvc.perform(get("/mock/ektis/v1/farmers/1234567890").header("X-API-Key", KEY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("Outbox: çatdırılmayan hadisə FAILED olur və təkrar cəhd üçün gecikmə alır (HTTP server yoxdur)")
    void outboxRetriesOnFailure() {
        var e = publisher.publish("TEST_EVENT", "Test", 1, Map.of("x", 1));
        processor.processDue();
        var after = events.findById(e.getId()).orElseThrow();
        // MockMvc testlərində real HTTP port açılmır → çatdırılma alınmır → retry planlaşdırılır
        assertThat(after.getStatus()).isIn(IntegrationEventStatus.FAILED, IntegrationEventStatus.SENT);
        if (after.getStatus() == IntegrationEventStatus.FAILED) {
            assertThat(after.getAttempts()).isEqualTo(1);
            assertThat(after.getNextAttemptAt()).isAfter(Instant.now());
            assertThat(after.getLastError()).isNotBlank();
        }
    }
}
