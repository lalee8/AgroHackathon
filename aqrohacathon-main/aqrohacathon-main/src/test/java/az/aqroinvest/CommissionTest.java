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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Qəbul edən tərəf: dövlət qurumlarından ibarət komissiya, səsvermə, kvorum və qərarın səs çoxluğuna uyğunluğu. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommissionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode read(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Komissiya (Qaydalar 3.5): 9 üzv, kvorum 2/3, bitərəf yoxdur, katib səs vermir, əleyhinə səs əsaslandırılır, protokol")
    void votingFlow() throws Exception {
        // Hamıya açıq: komissiyada təmsil olunan dövlət qurumları
        JsonNode inst = read(mvc.perform(get("/api/v1/competitions/commission")).andExpect(status().isOk()));
        assertThat(inst.size()).isEqualTo(7);

        String admin = TestSupport.login(mvc, mapper, "admin@aqroinvest.az", "Admin123!");
        String expert = TestSupport.login(mvc, mapper, "ekspert@aqroinvest.az", "Ekspert123!");
        String chair = TestSupport.login(mvc, mapper, "nazirlik@aqroinvest.az", "Admin123!");

        // Baxılan müraciət: seed-də 5 səs var, kvorum 6
        JsonNode list = read(mvc.perform(get("/api/v1/review/applications").param("status", "UNDER_REVIEW").header("Authorization", admin)));
        long id = list.get(0).path("id").asLong();
        JsonNode v = read(mvc.perform(get("/api/v1/review/applications/" + id + "/votes").header("Authorization", admin)).andExpect(status().isOk()));
        assertThat(v.path("tally").path("votingMembers").asInt()).isEqualTo(9);
        assertThat(v.path("tally").path("quorum").asInt()).isEqualTo(6);
        assertThat(v.path("tally").path("quorumReached").asBoolean()).isFalse();
        assertThat(v.path("canVote").asBoolean()).isFalse(); // katib səs vermir

        mvc.perform(post("/api/v1/admin/applications/" + id + "/status").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DECIDED\",\"decision\":\"APPROVED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_QUORUM"));
        mvc.perform(post("/api/v1/review/applications/" + id + "/votes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vote\":\"FOR\"}")).andExpect(status().isForbidden());

        // Bitərəf qalmağa yol verilmir, əleyhinə səs səbəbsiz qəbul olunmur
        mvc.perform(post("/api/v1/review/applications/" + id + "/votes").header("Authorization", expert).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vote\":\"ABSTAIN\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/review/applications/" + id + "/votes").header("Authorization", expert).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vote\":\"AGAINST\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.comment").exists());
        JsonNode after = read(mvc.perform(post("/api/v1/review/applications/" + id + "/votes").header("Authorization", expert)
                .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"FOR\",\"comment\":\"Biznes plan real\"}")).andExpect(status().isOk()));
        assertThat(after.path("myVote").asText()).isEqualTo("FOR");
        assertThat(after.path("tally").path("quorumReached").asBoolean()).isTrue();

        // Sədr da lehinə: səs çoxluğu toplanırsa, qalib elan etmək mümkündür; əks halda imtina qərarı tələb olunur
        JsonNode t = read(mvc.perform(post("/api/v1/review/applications/" + id + "/votes").header("Authorization", chair)
                .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"FOR\"}")).andExpect(status().isOk())).path("tally");
        String outcome = t.path("outcome").asText();
        String decision = "APPROVED".equals(outcome) ? "APPROVED" : "REJECTED";
        mvc.perform(post("/api/v1/admin/applications/" + id + "/status").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DECIDED\",\"decision\":\"" + decision + "\",\"comment\":\"Komissiyanın qərarı\"}"))
                .andExpect(status().isOk());
        // Qərardan sonra səsvermə bağlıdır
        mvc.perform(post("/api/v1/review/applications/" + id + "/votes").header("Authorization", expert).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vote\":\"AGAINST\",\"comment\":\"gec\"}")).andExpect(status().isConflict());

        // Protokol
        JsonNode app = read(mvc.perform(get("/api/v1/review/applications/" + id).header("Authorization", admin)));
        JsonNode protocol = read(mvc.perform(get("/api/v1/review/competitions/" + app.path("competition").path("id").asLong() + "/protocol")
                .header("Authorization", expert)).andExpect(status().isOk()));
        assertThat(protocol.path("members").size()).isEqualTo(10);
        assertThat(protocol.path("rows").size()).isGreaterThan(0);
        assertThat(protocol.path("rows").get(0).path("rank").asInt()).isEqualTo(1);
    }
}
