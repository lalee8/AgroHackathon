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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Torpağın potensialı, Monte-Carlo, sahibkar ↔ investor bazarı və alıcı niyyət məktubları. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PlotRepository plots;

    private long plot(String code) {
        return plots.findByCode(code).orElseThrow().getId();
    }

    private JsonNode json(String body) throws Exception {
        return mapper.readTree(body);
    }

    @Test
    @DisplayName("Əkinə yararsız torpaqda ən yaxşı istifadə günəş stansiyasıdır, əkin yerində isə GES təyinat dəyişikliyi tələb edir")
    void landPotentialRespectsDesignation() throws Exception {
        JsonNode solarLand = json(mvc.perform(get("/api/v1/plots/" + plot("FZL-006") + "/potential")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(solarLand.path("designation").asText()).isEqualTo("YARARSIZ");
        assertThat(solarLand.path("options").get(0).path("useCase").asText()).isEqualTo("GUNES");

        JsonNode arable = json(mvc.perform(get("/api/v1/plots/" + plot("FZL-001") + "/potential")).andReturn().getResponse().getContentAsString());
        for (JsonNode o : arable.path("options")) {
            if (o.path("useCase").asText().equals("GUNES")) {
                assertThat(o.path("permitted").asBoolean()).isFalse();
                assertThat(o.path("suitability").asInt()).isLessThanOrEqualTo(25);
            }
        }
    }

    @Test
    @DisplayName("Monte-Carlo: alıcı müqaviləsi pis ssenarini yaxşılaşdırır, nəticə təkrarlana biləndir")
    void monteCarloOfftakeReducesRisk() throws Exception {
        Map<String, Object> req = Map.of("plotId", plot("FZL-001"), "useCase", "EKINCILIK", "runs", 800, "offtakeShare", 0.8);
        String a = mvc.perform(post("/api/v1/simulate").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.fan.length()").value(11)).andReturn().getResponse().getContentAsString();
        String b = mvc.perform(post("/api/v1/simulate").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        JsonNode r = json(a).path("result");
        assertThat(json(b).path("result").path("summary")).isEqualTo(r.path("summary"));
        assertThat(r.path("summary").path("finalCashP10").asLong()).isGreaterThan(r.path("withoutOfftake").path("finalCashP10").asLong());
    }

    @Test
    @DisplayName("Hədəf kalkulyatoru: ayda 1 mln ₼ üçün lazım olan GES sahəyə sığmır")
    void goalCalculator() throws Exception {
        mvc.perform(get("/api/v1/simulate/goal").param("plotId", String.valueOf(plot("FZL-006"))).param("useCase", "GUNES").param("monthlyNet", "1000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasibleOnPlot").value(false))
                .andExpect(jsonPath("$.scaleP50").isNumber());
    }

    @Test
    @DisplayName("Reallıq yoxlaması şişirdilmiş gəlir iddiasını tapır")
    void realityCheckFlagsInflatedClaims() throws Exception {
        Map<String, Object> req = new java.util.HashMap<>(Map.of("sector", "TURIZM", "plotId", plot("CBR-006"), "title", "Test oteli",
                "scale", 12, "capex", 1_100_000, "ownFunds", 300_000, "askAmount", 800_000, "dealType", "EQUITY", "equityPercent", 40));
        req.put("claimedRevenue", 2_000_000);
        JsonNode a = json(mvc.perform(post("/api/v1/projects/reality-check").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(a.path("checks").get(0).path("severity").asText()).isEqualTo("CRIT");
        assertThat(a.path("credibility").asInt()).isLessThan(80);
        assertThat(a.path("terms").path("scenarios")).hasSize(3);
    }

    @Test
    @DisplayName("Maraq → razılıq: əlaqə məlumatı yalnız sahibkar razılıq verdikdən sonra açılır")
    void interestRevealsContactOnlyAfterAcceptance() throws Exception {
        String owner = TestSupport.login(mvc, mapper, "investor@aqroinvest.az", "Investor123!");
        Map<String, Object> req = Map.of("sector", "BAGCILIQ", "plotId", plot("QBD-001"), "title", "Test bağı", "scale", 20,
                "capex", 300_000, "ownFunds", 90_000, "askAmount", 210_000, "dealType", "LOAN", "interestPercent", 12, "publish", true);
        long projectId = json(mvc.perform(post("/api/v1/projects").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("card").path("id").asLong();

        String investor = TestSupport.register(mvc, mapper, "test.investor.market@example.az");
        mvc.perform(post("/api/v1/projects/" + projectId + "/interest").header("Authorization", investor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":100000,\"message\":\"Maraqlıdır\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", investor))
                .andExpect(jsonPath("$.myInterest.status").value("NEW"))
                .andExpect(jsonPath("$.ownerContact").doesNotExist());
        // sahibkar öz layihəsinə maraq bildirə bilməz
        mvc.perform(post("/api/v1/projects/" + projectId + "/interest").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":5000}")).andExpect(status().isBadRequest());

        JsonNode mine = json(mvc.perform(get("/api/v1/projects/mine").header("Authorization", owner)).andReturn().getResponse().getContentAsString());
        long interestId = -1;
        for (JsonNode m : mine) if (m.path("project").path("id").asLong() == projectId) interestId = m.path("interests").get(0).path("id").asLong();
        mvc.perform(post("/api/v1/projects/interests/" + interestId + "/respond").header("Authorization", investor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/projects/interests/" + interestId + "/respond").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true,\"reply\":\"Görüşək\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects/" + projectId).header("Authorization", investor))
                .andExpect(jsonPath("$.myInterest.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.ownerContact.email").value("investor@aqroinvest.az"));
    }

    @Test
    @DisplayName("Alıcı: niyyət məktubunu yalnız tələbin sahibi imzalaya bilər, investor alıcı API-sinə girə bilməz")
    void letterOfIntentFlow() throws Exception {
        String investor = TestSupport.login(mvc, mapper, "investor@aqroinvest.az", "Investor123!");
        String buyer = TestSupport.login(mvc, mapper, "alici@aqroinvest.az", "Alici123!");
        mvc.perform(get("/api/v1/buyer/demands").header("Authorization", investor)).andExpect(status().isForbidden());

        JsonNode demands = json(mvc.perform(get("/api/v1/demands").param("category", "BAGCILIQ")).andReturn().getResponse().getContentAsString());
        long demandId = -1;
        for (JsonNode d : demands) if (d.path("product").asText().equals("NAR")) demandId = d.path("id").asLong();
        assertThat(demandId).isPositive();
        long letterId = json(mvc.perform(post("/api/v1/demands/" + demandId + "/letters").header("Authorization", investor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"plotId\":" + plot("AGD-004") + ",\"volumeTons\":300}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asLong();

        String other = TestSupport.register(mvc, mapper, "test.buyer.other@example.az");
        mvc.perform(post("/api/v1/buyer/letters/" + letterId + "/sign").header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/buyer/letters/" + letterId + "/sign").header("Authorization", buyer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":600}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SIGNED"));
        mvc.perform(post("/api/v1/buyer/letters/" + letterId + "/sign").header("Authorization", buyer)).andExpect(status().isConflict());
    }
}
