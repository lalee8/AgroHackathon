package az.aqroinvest;

import az.aqroinvest.competition.Competition;
import az.aqroinvest.competition.CompetitionRepository;
import az.aqroinvest.integration.ektis.IntegrationEventRepository;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PlotRepository plots;
    @Autowired CompetitionRepository competitions;
    @Autowired IntegrationEventRepository events;

    record Target(long competitionId, long plotId) {}

    Target target(String plotCode) {
        Plot p = plots.findByCode(plotCode).orElseThrow();
        Competition c = competitions.findPublishedByPlot(p.getId()).get(0);
        return new Target(c.getId(), p.getId());
    }

    Map<String, Object> request(Target t) {
        Map<String, Object> m = new HashMap<>();
        m.put("competitionId", t.competitionId());
        m.put("plotId", t.plotId());
        m.put("applicantType", "LEGAL");
        m.put("companyName", "Test Aqro MMC");
        m.put("voen", "1234567890");
        m.put("fin", "AB12CD3");
        m.put("contactPhone", "+994501112233");
        m.put("contactEmail", "test@aqro.az");
        m.put("activityType", "BAGCILIQ");
        m.put("investmentAmount", 1_200_000);
        m.put("ownFundsPercent", 50);
        m.put("jobsPlanned", 40);
        m.put("implementationMonths", 24);
        m.put("experienceYears", 6);
        m.put("technologies", List.of("DRIP_IRRIGATION", "SOLAR", "UNKNOWN_TECH"));
        m.put("businessPlan", "Bu biznes plan nar bağının salınması, damcı suvarma və soyuducu anbarın tikintisini nəzərdə tutur. ".repeat(4));
        m.put("expectedAnnualRevenue", 600_000);
        m.put("idpStatus", true);
        m.put("consentGiven", true);
        return m;
    }

    @Test
    @DisplayName("Tam axın: qaralama → sənəd → göndərmə → baxış → qərar; EKTİS hadisələri outbox-a yazılır")
    void fullApplicationLifecycle() throws Exception {
        String investor = TestSupport.register(mvc, mapper, "flow-" + System.nanoTime() + "@test.az");
        String admin = TestSupport.login(mvc, mapper, "admin@aqroinvest.az", "Admin123!");
        Target t = target("AGD-004");
        long eventsBefore = events.count();

        JsonNode draft = json(mvc.perform(post("/api/v1/applications").header("Authorization", investor)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request(t))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.technologies.length()").value(2)) // naməlum texnologiya atılır
                .andReturn().getResponse().getContentAsString());
        long id = draft.get("id").asLong();
        assertThat(draft.get("missingRequirements").toString()).contains("İnvestisiya layihəsi").contains("Şəxsiyyət").contains("İştirak haqqı");

        // Natamam müraciət göndərilə bilməz
        mvc.perform(post("/api/v1/applications/" + id + "/submit").header("Authorization", investor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INCOMPLETE_APPLICATION"));

        // Saxta PDF (məzmun yoxlaması) rədd edilir
        mvc.perform(multipart("/api/v1/applications/" + id + "/documents").file(new MockMultipartFile("file", "x.pdf", "application/pdf", "fake".getBytes()))
                        .param("type", "BUSINESS_PLAN").header("Authorization", investor))
                .andExpect(status().isUnsupportedMediaType());

        for (String type : List.of("BUSINESS_PLAN", "IDENTITY", "TAX_CERTIFICATE", "BANK_REFERENCE", "EXPERIENCE_PROOF", "FEE_RECEIPT")) {
            mvc.perform(multipart("/api/v1/applications/" + id + "/documents")
                            .file(new MockMultipartFile("file", "../../etc/" + type + ".pdf", "application/pdf", TestSupport.PDF))
                            .param("type", type).header("Authorization", investor))
                    .andExpect(status().isOk());
        }

        JsonNode submitted = json(mvc.perform(post("/api/v1/applications/" + id + "/submit").header("Authorization", investor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.documents[0].name").value("BUSINESS_PLAN.pdf")) // path traversal təmizlənir
                .andReturn().getResponse().getContentAsString());
        String number = submitted.get("appNumber").asText();
        assertThat(number).matches("AQR-\\d{4}-\\d{6}");
        assertThat(submitted.get("autoScore").asInt()).isBetween(40, 100);
        assertThat(submitted.get("ektisFarmerStatus").asText()).isNotBlank();

        // Girişsiz izləmə: yanlış kod → 404, düzgün kod → status
        mvc.perform(get("/api/v1/track").param("number", number).param("code", "WRONG123")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/track").param("number", number).param("code", submitted.get("trackingCode").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.applicant").value("Tes***"));

        // Qeyri-mümkün keçid: SUBMITTED → DECIDED
        mvc.perform(post("/api/v1/admin/applications/" + id + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DECIDED\",\"decision\":\"APPROVED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

        // Əlavə məlumat sorğusu şərhsiz qəbul edilmir
        mvc.perform(post("/api/v1/admin/applications/" + id + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INFO_REQUESTED\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/admin/applications/" + id + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INFO_REQUESTED\",\"comment\":\"Bank arayışı əlavə edin\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INFO_REQUESTED"));

        // İnvestor cavab verir → yenidən baxılır
        mvc.perform(post("/api/v1/applications/" + id + "/respond").header("Authorization", investor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Arayış əlavə edildi\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        // Ekspert qiymətləndirməsi
        String expert = TestSupport.login(mvc, mapper, "ekspert@aqroinvest.az", "Ekspert123!");
        mvc.perform(post("/api/v1/review/applications/" + id + "/evaluations").header("Authorization", expert)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scores\":{\"investment\":8,\"jobs\":8,\"businessPlan\":7,\"innovation\":9,\"ownFunds\":8,\"documents\":9,\"social\":10},\"comment\":\"Yaxşı\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expertAverage").value(82))
                // ekspert şəxsi identifikatorları maskalanmış görür
                .andExpect(jsonPath("$.fin").value("AB****3"));

        // Ekspert statusu dəyişə bilməz
        mvc.perform(post("/api/v1/admin/applications/" + id + "/status").header("Authorization", expert)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DECIDED\",\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/admin/applications/" + id + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DECIDED\",\"decision\":\"APPROVED\",\"comment\":\"Qalib\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECIDED"))
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.history.length()").value(5));

        mvc.perform(get("/api/v1/plots/" + t.plotId())).andExpect(jsonPath("$.status").value("AWARDED"));

        // İnvestora bildiriş getdi
        mvc.perform(get("/api/v1/notifications").header("Authorization", investor))
                .andExpect(jsonPath("$.items[0].title").value("Təbriklər! Müraciətiniz təsdiqləndi"));

        // EKTİS hadisələri: SUBMITTED + 3 status dəyişikliyi + DECIDED
        assertThat(events.count() - eventsBefore).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("IDOR qorunması: investor başqasının müraciətini görə bilməz; təkrar müraciət bloklanır")
    void accessControl() throws Exception {
        String owner = TestSupport.register(mvc, mapper, "owner-" + System.nanoTime() + "@test.az");
        String stranger = TestSupport.register(mvc, mapper, "stranger-" + System.nanoTime() + "@test.az");
        Target t = target("FZL-003");
        long id = json(mvc.perform(post("/api/v1/applications").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request(t))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/v1/applications/" + id).header("Authorization", stranger)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/applications/" + id).header("Authorization", stranger)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request(t)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/applications/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/stats").header("Authorization", owner)).andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/applications").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request(t))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_APPLICATION"));
    }

    @Test
    @DisplayName("Giriş: yanlış şifrə → 401, 5 uğursuz cəhddən sonra hesab bloklanır")
    void loginLockout() throws Exception {
        String email = "lock-" + System.nanoTime() + "@test.az";
        TestSupport.register(mvc, mapper, email);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"Wrong1234\"}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Test12345\"}"))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("Qeydiyyat validasiyası: zəif şifrə və razılıq olmadan qeydiyyat mümkün deyil")
    void registrationValidation() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"A\",\"email\":\"bad\",\"password\":\"short\",\"consent\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.consent").exists());
    }

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }
}
