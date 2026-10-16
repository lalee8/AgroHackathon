package az.aqroinvest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fəaliyyət planı: iqlim, təqvim, su balansı, ehtiyaclar, Qaydaların minimumları; rəsmi statistika və TAM/SAM/SOM. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode read(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Bağ planı: aran zonası, NASA iqlimi, aylıq su balansı, ting sayı, Qaydaların ting minimumu")
    void orchardPlan() throws Exception {
        long id = 0;
        for (JsonNode p : read(mvc.perform(get("/api/v1/plots")))) if (p.path("code").asText().equals("AGD-004")) id = p.path("id").asLong();
        JsonNode d = read(mvc.perform(get("/api/v1/plots/" + id + "/plan").param("use", "BAGCILIQ")).andExpect(status().isOk()));
        assertThat(d.path("zone").asText()).isEqualTo("lowland");
        assertThat(d.path("item").path("id").asText()).isEqualTo("pomegranate");
        assertThat(d.path("climate").path("months").path("T2M").size()).isEqualTo(12);
        assertThat(d.path("water").path("irrigationM3Total").asDouble()).isPositive();
        assertThat(d.path("calendar").size()).isGreaterThan(5);
        assertThat(d.path("first12").size()).isGreaterThan(5);
        assertThat(d.path("rules").toString()).contains("650 ting");
        assertThat(d.path("needs").toString()).contains("ting");
        // Başqa bitki və istiqamət
        assertThat(read(mvc.perform(get("/api/v1/plots/" + id + "/plan").param("use", "BAGCILIQ").param("item", "hazelnut"))).path("item").path("id").asText()).isEqualTo("hazelnut");
        JsonNode solar = read(mvc.perform(get("/api/v1/plots/" + id + "/plan").param("use", "GUNES")).andExpect(status().isOk()));
        assertThat(solar.path("needs").toString()).contains("MVt");
        mvc.perform(get("/api/v1/plots/" + id + "/plan").param("use", "XXX")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rəsmi göstəricilər: 4 müsabiqə, 15 253 ha icarə, TAM > SAM > SOM")
    void officialStats() throws Exception {
        JsonNode o = read(mvc.perform(get("/api/v1/impact/official")).andExpect(status().isOk()));
        assertThat(o.path("competitions").asInt()).isEqualTo(4);
        assertThat(o.path("byDistrict").size()).isEqualTo(9);
        assertThat(o.path("leasedHa").asDouble()).isBetween(15250.0, 15260.0);
        JsonNode m = o.path("market");
        assertThat(m.get(0).path("value").asDouble()).isGreaterThan(m.get(1).path("value").asDouble());
        assertThat(m.get(1).path("value").asDouble()).isGreaterThan(m.get(2).path("value").asDouble());
    }
}
