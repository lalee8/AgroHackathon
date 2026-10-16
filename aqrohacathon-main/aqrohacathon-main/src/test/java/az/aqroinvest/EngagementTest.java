package az.aqroinvest;

import az.aqroinvest.plot.PlotRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sevimlilər və müsabiqə bildirişi, vəd və reallıq, paylaşım önizləməsi, valyuta məzənnələri. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EngagementTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PlotRepository plots;

    @Test
    @DisplayName("Sevimli sahə müsabiqəyə çıxanda izləyən investor bildiriş alır")
    void watchAndNotify() throws Exception {
        String inv = TestSupport.login(mvc, mapper, "investor@aqroinvest.az", "Investor123!");
        long plotId = plots.findByCode("KLB-001").orElseThrow().getId();
        mvc.perform(post("/api/v1/plots/" + plotId + "/watch")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/plots/" + plotId + "/watch").header("Authorization", inv))
                .andExpect(status().isOk()).andExpect(jsonPath("$.watching").value(true));
        mvc.perform(get("/api/v1/me/watches").header("Authorization", inv))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.code == 'KLB-001')].categoryLabel").exists());

        String creator = TestSupport.login(mvc, mapper, "admin@aqroinvest.az", "Admin123!");
        String approver = TestSupport.login(mvc, mapper, "nazirlik@aqroinvest.az", "Admin123!");
        String body = mapper.writeValueAsString(Map.of("title", "Kəlbəcər test müsabiqəsi", "description", "Sevimlilər testi",
                "startAt", Instant.now().minus(1, ChronoUnit.HOURS).toString(), "deadlineAt", Instant.now().plus(30, ChronoUnit.DAYS).toString(),
                "plotIds", List.of(plotId)));
        long id = mapper.readTree(mvc.perform(post("/api/v1/admin/competitions").header("Authorization", creator)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/v1/admin/competitions/" + id + "/submit").header("Authorization", creator)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/competitions/" + id + "/approve").header("Authorization", approver)).andExpect(status().isOk());

        JsonNode n = mapper.readTree(mvc.perform(get("/api/v1/notifications").header("Authorization", inv)).andReturn().getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode item : n.get("items")) found |= item.get("title").asText().contains("Sevimli sahəniz müsabiqəyə çıxdı: KLB-001");
        assertThat(found).isTrue();

        mvc.perform(delete("/api/v1/plots/" + plotId + "/watch").header("Authorization", inv))
                .andExpect(status().isOk()).andExpect(jsonPath("$.watching").value(false));
    }

    @Test
    @DisplayName("Vəd və reallıq: qalib müraciətin öhdəlikləri, aylıq icra və peyk yoxlaması; qalibi olmayan sahə 404")
    void delivery() throws Exception {
        long awarded = plots.findByCode("XCV-001").orElseThrow().getId();
        mvc.perform(get("/api/v1/impact/delivery/" + awarded)).andExpect(status().isOk())
                .andExpect(jsonPath("$.promised.investment").value(1650000))
                .andExpect(jsonPath("$.promised.jobs").value(48))
                .andExpect(jsonPath("$.checks[?(@.key == 'investment')]").exists())
                .andExpect(jsonPath("$.checks[?(@.key == 'ndvi')]").exists())
                .andExpect(jsonPath("$.verdict").isNotEmpty());
        mvc.perform(get("/api/v1/impact/delivery")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        long open = plots.findByCode("AGD-001").orElseThrow().getId();
        mvc.perform(get("/api/v1/impact/delivery/" + open)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NO_WINNER"));
    }

    @Test
    @DisplayName("Paylaşım linki Open Graph teqləri ilə sahənin adını, şərtlərini və peyk şəklini verir")
    void sharePreview() throws Exception {
        long plotId = plots.findByCode("AGD-004").orElseThrow().getId();
        String html = mvc.perform(get("/p/" + plotId).header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "aqro.example.az"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("og:title", "AGD-004", "https://aqro.example.az/api/v1/plots/" + plotId + "/imagery/2025",
                "url=/sahe.html?id=" + plotId);
        mvc.perform(get("/p/999999").accept(MediaType.TEXT_HTML)).andExpect(status().isNotFound());
        mvc.perform(get("/p/abc").accept(MediaType.TEXT_HTML)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Valyuta məzənnələri: Mərkəzi Bank və ya son məlum məzənnə")
    void rates() throws Exception {
        mvc.perform(get("/api/v1/reference/rates")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rates.USD").isNumber()).andExpect(jsonPath("$.rates.EUR").isNumber());
    }
}
