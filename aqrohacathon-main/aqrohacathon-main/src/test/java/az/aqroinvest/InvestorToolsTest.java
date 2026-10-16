package az.aqroinvest;

import az.aqroinvest.economics.ModelFactory;
import az.aqroinvest.economics.MonteCarloService;
import az.aqroinvest.economics.MonteCarloService.Exposure;
import az.aqroinvest.economics.SimulationSpec;
import az.aqroinvest.economics.UseCase;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Portfel qurucusu, korrelyasiyalı Monte-Carlo, peyk təsdiqli mərhələli maliyyələşmə, Böyük Qayıdış paneli, memorandum. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvestorToolsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PlotRepository plots;
    @Autowired ModelFactory models;
    @Autowired MonteCarloService mc;

    private JsonNode get200(String url, String auth) throws Exception {
        var req = get(url);
        if (auth != null) req = req.header("Authorization", auth);
        return mapper.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Ortaq dünya: eyni zonadakı iki bağ birlikdə düşür, günəş stansiyası isə quraqlıqdan asılı deyil")
    void correlatedWorld() {
        Plot a = plots.findByCode("AGD-004").orElseThrow(), b = plots.findByCode("CBR-002").orElseThrow(), s = plots.findByCode("FZL-006").orElseThrow();
        List<SimulationSpec> specs = List.of(models.build(a, UseCase.BAGCILIQ, ModelFactory.Overrides.none()),
                models.build(b, UseCase.BAGCILIQ, ModelFactory.Overrides.none()), models.build(s, UseCase.GUNES, ModelFactory.Overrides.none()));
        List<Exposure> ex = List.of(Exposure.of(UseCase.BAGCILIQ, 0), Exposure.of(UseCase.BAGCILIQ, 0), Exposure.of(UseCase.GUNES, 0));
        MonteCarloService.Joint j = mc.joint(specs, ex, 600, 7);
        double orchards = corr(j.npv()[0], j.npv()[1]), hedge = corr(j.npv()[0], j.npv()[2]);
        assertThat(orchards).isGreaterThan(0.15);
        assertThat(hedge).isLessThan(orchards - 0.15);
    }

    @Test
    @DisplayName("Portfel: büdcə, mövqe, istiqamət və torpaq limitlərinə əməl edir, diversifikasiya riski azaldır")
    void portfolioRespectsConstraints() throws Exception {
        JsonNode r = get200("/api/v1/portfolio?budget=3000000&risk=50&horizon=15", null);
        long budget = 3_000_000;
        JsonNode t = r.path("totals");
        assertThat(t.path("invested").asLong()).isPositive().isLessThanOrEqualTo(budget);
        assertThat(r.path("positions").size()).isGreaterThanOrEqualTo(3);
        Map<String, Long> byUse = new HashMap<>();
        Map<String, Double> land = new HashMap<>();
        for (JsonNode p : r.path("positions")) {
            assertThat(p.path("amount").asLong()).isLessThanOrEqualTo(Math.max(budget * 35 / 100, 250_000) + 1);
            byUse.merge(p.path("use").asText(), p.path("amount").asLong(), Long::sum);
            if (p.path("unit").asText().equals("ha")) land.merge(p.path("code").asText(), p.path("scale").asDouble(), Double::sum);
        }
        byUse.values().forEach(v -> assertThat(v).isLessThanOrEqualTo(Math.max(budget * 40 / 100, 300_000) + 1));
        land.forEach((code, ha) -> assertThat(ha).isLessThanOrEqualTo(plots.findByCode(code).orElseThrow().getAreaHa().doubleValue() + 0.01));
        assertThat(r.path("diversification").path("benefitPct").asDouble()).isPositive();
        assertThat(r.path("frontier").findValuesAsText("efficient")).contains("true");
        assertThat(r.path("stress").size()).isPositive();
        assertThat(r.path("impact").path("directJobs").asInt()).isPositive();
        assertThat(r.path("summary").toString()).doesNotContain("—");
        mvc.perform(get("/api/v1/portfolio?budget=10")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/portfolio/explain").contentType("application/json").content("{\"budget\":1000000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("offline"));
    }

    @Test
    @DisplayName("Mərhələli maliyyələşmə: peyk işi təsdiqləyir (hava effekti çıxılmaqla), tikinti izi olmayanda ödənişi dayandırır, ardıcıllıq qorunur")
    void satelliteVerifiedTranches() throws Exception {
        String inv = TestSupport.login(mvc, mapper, "investor@aqroinvest.az", "Investor123!");
        Long wine = null, solar = null;
        for (JsonNode i : get200("/api/v1/projects/interests/mine", inv)) {
            if (!i.path("status").asText().equals("ACCEPTED")) continue;
            if (i.path("projectTitle").asText().contains("üzüm")) wine = i.path("id").asLong();
            if (i.path("projectTitle").asText().contains("günəş")) solar = i.path("id").asLong();
        }
        assertThat(wine).isNotNull();
        assertThat(solar).isNotNull();

        JsonNode w = get200("/api/v1/projects/interests/" + wine + "/tranches", inv);
        JsonNode start = w.path("milestones").get(1);
        assertThat(start.path("key").asText()).isEqualTo("SAT_START");
        assertThat(start.path("status").asText()).isEqualTo("VERIFIED");
        JsonNode ev = start.path("evidence");
        assertThat(Math.abs(ev.path("excess").asDouble())).isGreaterThanOrEqualTo(0.05);
        assertThat(ev.path("excess").asDouble()).isCloseTo(ev.path("plotChange").asDouble() - ev.path("controlChange").asDouble(), org.assertj.core.data.Offset.offset(0.002));
        assertThat(ev.path("controls").asInt()).isGreaterThanOrEqualTo(3);

        JsonNode s = get200("/api/v1/projects/interests/" + solar + "/tranches", inv);
        assertThat(s.path("milestones").get(1).path("status").asText()).isEqualTo("FLAGGED");
        mvc.perform(post("/api/v1/projects/interests/" + solar + "/tranches/2/release").header("Authorization", inv)).andExpect(status().isBadRequest());

        // 4-cü mərhələ (alıcı imzalayıb) təsdiqlənib, amma 3-cü gözlənilir: ardıcıllıq pozula bilməz
        mvc.perform(post("/api/v1/projects/interests/" + wine + "/tranches/4/release").header("Authorization", inv))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OUT_OF_ORDER"));
        long before = w.path("released").asLong();
        JsonNode after = mapper.readTree(mvc.perform(post("/api/v1/projects/interests/" + wine + "/tranches/2/release").header("Authorization", inv))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(after.path("released").asLong()).isEqualTo(before + start.path("amount").asLong());
        mvc.perform(post("/api/v1/projects/interests/" + wine + "/tranches/2/release").header("Authorization", inv))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ALREADY_RELEASED"));

        // kənar istifadəçi razılaşmanı görmür
        String buyer = TestSupport.login(mvc, mapper, "alici@aqroinvest.az", "Alici123!");
        mvc.perform(get("/api/v1/projects/interests/" + wine + "/tranches").header("Authorization", buyer)).andExpect(status().isNotFound());
        // açıq şablon: payların cəmi 100%
        long projectId = after.path("projectId").asLong();
        double sum = 0;
        for (JsonNode m : get200("/api/v1/projects/" + projectId + "/tranches", null)) sum += m.path("sharePct").asDouble();
        assertThat(sum).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Böyük Qayıdış paneli, layihənin sosial təsiri və memorandum (oflayn AI)")
    void impactAndMemo() throws Exception {
        JsonNode r = get200("/api/v1/impact/regions", null);
        assertThat(r.path("districts").size()).isEqualTo(11);
        int prev = 101;
        for (JsonNode d : r.path("districts")) {
            int score = d.path("readiness").path("total").asInt();
            assertThat(score).isBetween(0, 100).isLessThanOrEqualTo(prev);
            prev = score;
        }
        assertThat(r.path("totals").path("potential").path("families").asInt()).isPositive();
        JsonNode projects = get200("/api/v1/projects", null);
        long id = projects.get(0).path("id").asLong();
        JsonNode detail = get200("/api/v1/projects/" + id, null);
        assertThat(detail.path("analysis").path("impact").path("directJobs").asInt()).isPositive();
        JsonNode memo = mapper.readTree(mvc.perform(post("/api/v1/projects/" + id + "/memo")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(memo.path("mode").asText()).isEqualTo("offline");
        assertThat(memo.path("text").asText()).contains("**Tövsiyə**").doesNotContain("—");
    }

    @Test
    @DisplayName("UX müqavilələri: izahlı bal + canlı sayğac, son tarixə görə sıralama, tək \"Ən uyğun\"")
    void uxContracts() throws Exception {
        String body = "{\"budget\":1500000,\"categories\":[\"BAGCILIQ\",\"UZUMCULUK\"],\"needIrrigation\":true}";
        JsonNode sum = mapper.readTree(mvc.perform(post("/api/v1/recommendations/summary").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode list = mapper.readTree(mvc.perform(post("/api/v1/recommendations").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        int suitable = sum.path("suitable").asInt();
        assertThat(suitable).isPositive().isLessThanOrEqualTo(sum.path("total").asInt());
        // düymədə yazılan say qədər sahə göstərilir və hər balın izahı var
        assertThat(list.size()).isGreaterThanOrEqualTo(suitable);
        for (JsonNode m : list) {
            int parts = 0;
            for (JsonNode x : m.path("breakdown")) parts += x.path("points").asInt();
            assertThat(m.path("score").asInt()).isCloseTo(Math.max(0, Math.min(100, parts)), org.assertj.core.data.Offset.offset(3));
        }
        assertThat(list.get(0).path("score").asInt()).isEqualTo(sum.path("best").asInt());

        // Kataloq defoltu: müraciəti açıq sahələr əvvəl, son tarixi ən yaxın olan birinci
        JsonNode plotsByDeadline = get200("/api/v1/plots?sort=deadline", null);
        boolean seenClosed = false;
        String prev = "";
        for (JsonNode p : plotsByDeadline) {
            boolean open = p.path("competition").path("open").asBoolean(false);
            if (!open) { seenClosed = true; continue; }
            assertThat(seenClosed).as("açıq sahə bağlıdan sonra gəlməməlidir").isFalse();
            String d = p.path("competition").path("deadlineAt").asText();
            assertThat(d.compareTo(prev)).isGreaterThanOrEqualTo(0);
            prev = d;
        }

        // Simulyator: sıra göstərilən bala görədir, "Ən uyğun" yalnız birincidədir
        long id = plots.findByCode("FZL-001").orElseThrow().getId();
        JsonNode opts = get200("/api/v1/plots/" + id + "/potential", null).path("options");
        int best = 0, prevScore = 101;
        for (JsonNode o : opts) {
            assertThat(o.path("suitability").asInt()).isLessThanOrEqualTo(prevScore);
            prevScore = o.path("suitability").asInt();
            if (o.path("verdict").asText().equals("Ən uyğun")) best++;
        }
        assertThat(best).isLessThanOrEqualTo(1);
        if (best == 1) assertThat(opts.get(0).path("verdict").asText()).isEqualTo("Ən uyğun");
    }

    private static double corr(double[] a, double[] b) {
        double ma = Arrays.stream(a).average().orElse(0), mb = Arrays.stream(b).average().orElse(0), ab = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) {
            ab += (a[i] - ma) * (b[i] - mb);
            aa += (a[i] - ma) * (a[i] - ma);
            bb += (b[i] - mb) * (b[i] - mb);
        }
        return ab / Math.sqrt(aa * bb);
    }
}
