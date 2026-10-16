package az.aqroinvest;

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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Texnika icarəsi: elanlar, sorğu və qərar, "al və ya icarə" müqayisəsi. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MachineryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode read(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Al və ya icarə: icarədə ilkin investisiya azalır, gəlirlilik artır; sahəyə uyğun texnika təklif olunur")
    void buyVsRent() throws Exception {
        JsonNode plots = read(mvc.perform(get("/api/v1/plots")));
        long grain = 0;
        for (JsonNode p : plots) if (p.path("code").asText().equals("AGD-001")) grain = p.path("id").asLong();
        JsonNode m = read(mvc.perform(get("/api/v1/plots/" + grain + "/machinery")).andExpect(status().isOk()));
        JsonNode c = m.path("compare");
        assertThat(c.path("rent").path("capex").asLong()).isLessThan(c.path("buy").path("capex").asLong());
        assertThat(c.path("buy").path("capex").asLong() - c.path("rent").path("capex").asLong()).isEqualTo(c.path("machineryCapex").asLong());
        assertThat(c.path("rent").path("roiPct").asDouble()).isGreaterThan(c.path("buy").path("roiPct").asDouble());
        assertThat(m.path("needed").toString()).contains("COMBINE");
        for (JsonNode e : m.path("listings")) assertThat(e.path("suitable").toString()).contains("EKINCILIK");
        assertThat(read(mvc.perform(get("/api/v1/equipment"))).size()).isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("İcarə sorğusu: sahibinə bildiriş, yalnız sahibi qərar verir, öz texnikası icarəyə götürülmür")
    void rentFlow() throws Exception {
        String inv = TestSupport.login(mvc, mapper, "investor@aqroinvest.az", "Investor123!");
        String owner = TestSupport.login(mvc, mapper, "aqroservis@example.az", "Texnika123!");
        long tractor = 0;
        for (JsonNode e : read(mvc.perform(get("/api/v1/equipment").param("type", "TRACTOR")))) tractor = e.path("id").asLong();
        String body = "{\"startDate\":\"" + LocalDate.now().plusDays(5) + "\",\"quantity\":12,\"message\":\"test\"}";
        JsonNode q = read(mvc.perform(post("/api/v1/equipment/" + tractor + "/requests").header("Authorization", inv)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()));
        assertThat(q.path("status").asText()).isEqualTo("PENDING");
        assertThat(q.path("totalPrice").asDouble()).isEqualTo(12 * 180.0);
        // sahibi öz texnikasını icarəyə götürə bilməz
        mvc.perform(post("/api/v1/equipment/" + tractor + "/requests").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        // keçmiş tarix qəbul edilmir
        mvc.perform(post("/api/v1/equipment/" + tractor + "/requests").header("Authorization", inv).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2020-01-01\",\"quantity\":3}")).andExpect(status().isBadRequest());
        // başqası qərar verə bilməz
        mvc.perform(post("/api/v1/equipment/requests/" + q.path("id").asLong() + "/decide").header("Authorization", inv)
                .contentType(MediaType.APPLICATION_JSON).content("{\"accept\":true}")).andExpect(status().isNotFound());
        JsonNode d = read(mvc.perform(post("/api/v1/equipment/requests/" + q.path("id").asLong() + "/decide").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"accept\":true}")).andExpect(status().isOk()));
        assertThat(d.path("status").asText()).isEqualTo("ACCEPTED");
        JsonNode mine = read(mvc.perform(get("/api/v1/equipment/mine").header("Authorization", owner)));
        assertThat(mine.path("incoming").size()).isPositive();
        // elan vermək üçün giriş tələb olunur
        mvc.perform(post("/api/v1/equipment").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }
}
