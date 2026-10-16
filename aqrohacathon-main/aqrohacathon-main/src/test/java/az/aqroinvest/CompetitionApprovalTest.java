package az.aqroinvest;

import az.aqroinvest.plot.PlotRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompetitionApprovalTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PlotRepository plots;

    @Test
    @DisplayName("'Dörd göz': yaradan admin təsdiqləyə bilməz, digər admin dərc edir")
    void fourEyesApproval() throws Exception {
        String creator = TestSupport.login(mvc, mapper, "admin@aqroinvest.az", "Admin123!");
        String approver = TestSupport.login(mvc, mapper, "nazirlik@aqroinvest.az", "Admin123!");
        long plotId = plots.findByCode("TRT-001").orElseThrow().getId();

        String body = mapper.writeValueAsString(Map.of(
                "title", "Test müsabiqəsi", "description", "Avtomatik test",
                "startAt", Instant.now().minus(1, ChronoUnit.HOURS).toString(),
                "deadlineAt", Instant.now().plus(20, ChronoUnit.DAYS).toString(),
                "plotIds", List.of(plotId)));
        String created = mvc.perform(post("/api/v1/admin/competitions").header("Authorization", creator)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).get("id").asLong();

        mvc.perform(post("/api/v1/admin/competitions/" + id + "/approve").header("Authorization", approver))
                .andExpect(status().isConflict()); // hələ təsdiqə göndərilməyib

        mvc.perform(post("/api/v1/admin/competitions/" + id + "/submit").header("Authorization", creator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        mvc.perform(post("/api/v1/admin/competitions/" + id + "/approve").header("Authorization", creator))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/admin/competitions/" + id + "/approve").header("Authorization", approver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.approvedBy").value("Leyla Həsənova"));
    }

    @Test
    @DisplayName("Meyar çəkilərinin cəmi 100 olmalıdır")
    void criteriaMustSumTo100() throws Exception {
        String admin = TestSupport.login(mvc, mapper, "admin@aqroinvest.az", "Admin123!");
        long plotId = plots.findByCode("TRT-002").orElseThrow().getId();
        String body = mapper.writeValueAsString(Map.of(
                "title", "Yanlış çəkilər", "startAt", Instant.now().toString(),
                "deadlineAt", Instant.now().plus(5, ChronoUnit.DAYS).toString(), "plotIds", List.of(plotId),
                "criteria", List.of(Map.of("key", "investment", "label", "İnvestisiya", "weight", 60),
                        Map.of("key", "jobs", "label", "İş yerləri", "weight", 30))));
        mvc.perform(post("/api/v1/admin/competitions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CRITERIA_WEIGHT"));
    }
}
