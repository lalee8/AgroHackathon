package az.aqroinvest;

import az.aqroinvest.market.EntrepreneurProfileRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Qeydiyyat: üç hüquqi status (fiziki, hüquqi şəxs, xarici vətəndaş), məqsəd (investor / sahibkar) və mygov ID ilə giriş. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntrepreneurProfileRepository entrepreneurs;

    private JsonNode json(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Sahibkar qeydiyyatı öz sahələri ilə (rayon, istiqamət, torpaq, lazım olan məbləğ) saxlanılır")
    void entrepreneurRegistration() throws Exception {
        String body = """
                {"fullName":"Test Sahibkar","email":"sahibkar.test@example.az","password":"Sahibkar123","consent":true,"fin":"SB12KR4",
                 "accountType":"ENTREPRENEUR","entrepreneur":{"district":"AGDAM","sector":"BAGCILIQ","landStatus":"APPLYING","landHa":40,"needAmount":600000,"idea":"40 ha nar bağı"}}""";
        JsonNode r = json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()));
        assertThat(r.path("user").path("userType").asText()).isEqualTo("ENTREPRENEUR");
        assertThat(r.path("user").path("roleLabel").asText()).isEqualTo("Sahibkar");
        var p = entrepreneurs.findById(r.path("user").path("id").asLong()).orElseThrow();
        assertThat(p.getNeedAmount()).isEqualByComparingTo("600000");
        assertThat(p.getIdea()).isEqualTo("40 ha nar bağı");

        String inv = """
                {"fullName":"Test İnvestor","email":"investor.test@example.az","password":"Investor123","consent":true,"fin":"IN12VS4",
                 "accountType":"INVESTOR","investor":{"ticketMin":200000,"ticketMax":100000,"sectors":["BAGCILIQ"],"risk":"LOW"}}""";
        // maksimum minimumdan az ola bilməz
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(inv)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("mygov ID: QR sessiyası, təsdiq, birdəfəlik kod; mövcud FİN mövcud hesaba, yeni FİN yeni hesaba aparır")
    void mygovFlow() throws Exception {
        // mövcud investor (FİN 5ZK8M2P)
        JsonNode t = login("c1");
        assertThat(t.path("user").path("email").asText()).isEqualTo("investor@aqroinvest.az");
        assertThat(t.path("user").path("mygov").asBoolean()).isTrue();

        // kod ikinci dəfə işləmir
        String state = json(mvc.perform(post("/api/v1/auth/mygov/start"))).path("state").asText();
        mvc.perform(post("/api/v1/mock/mygov/approve").contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"" + state + "\",\"citizenId\":\"c1\"}"));
        String code = json(mvc.perform(get("/api/v1/auth/mygov/status").param("state", state))).path("code").asText();
        mvc.perform(post("/api/v1/auth/mygov/exchange").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/mygov/exchange").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/mygov/exchange").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"saxta\"}")).andExpect(status().isUnauthorized());

        // yeni vətəndaş: hesab yaranır, növ sonra seçilir
        JsonNode n = login("c3");
        assertThat(n.path("user").path("userType").isNull()).isTrue();
        String auth = "Bearer " + n.path("token").asText();
        JsonNode u = json(mvc.perform(put("/api/v1/auth/me/type").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountType\":\"ENTREPRENEUR\",\"entrepreneur\":{\"district\":\"FUZULI\",\"sector\":\"ENERJI\",\"needAmount\":900000}}"))
                .andExpect(status().isOk()));
        assertThat(u.path("userType").asText()).isEqualTo("ENTREPRENEUR");
        assertThat(u.path("legalType").asText()).isEqualTo("INDIVIDUAL");
        // mygov ID yalnız Azərbaycan vətəndaşları üçündür: xarici vətəndaş statusu seçilə bilməz
        mvc.perform(put("/api/v1/auth/me/type").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountType\":\"INVESTOR\",\"legal\":{\"legalType\":\"FOREIGN\",\"citizenship\":\"X\",\"passportNo\":\"AB12345\"}}"))
                .andExpect(status().isBadRequest());
        // ... amma şirkətin nümayəndəsi kimi davam edə bilər
        JsonNode c = json(mvc.perform(put("/api/v1/auth/me/type").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountType\":\"INVESTOR\",\"legal\":{\"legalType\":\"COMPANY\",\"companyName\":\"Mygov Aqro MMC\",\"voen\":\"1100220033\"}}"))
                .andExpect(status().isOk()));
        assertThat(c.path("legalTypeLabel").asText()).isEqualTo("Hüquqi şəxs");
        // eyni vətəndaşın ikinci girişi eyni hesaba gəlir
        assertThat(login("c3").path("user").path("id").asLong()).isEqualTo(n.path("user").path("id").asLong());
    }

    @Test
    @DisplayName("Üç hüquqi status: fiziki şəxs FİN, hüquqi şəxs VÖEN, xarici vətəndaş pasport ilə; yerli sakin qeyd olunur")
    void legalTypes() throws Exception {
        // Fiziki şəxs FİN-siz qeydiyyatdan keçə bilməz
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Ad Soyad","email":"fiziki.fin@example.az","password":"Parol12345","consent":true,"legal":{"legalType":"INDIVIDUAL"}}"""))
                .andExpect(status().isBadRequest()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.fieldErrors.fin").exists());
        // Yerli sakin
        JsonNode local = json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Yerli Sakin","email":"yerli@example.az","password":"Parol12345","consent":true,"fin":"YS12AB3",
                 "legal":{"legalType":"INDIVIDUAL","localResident":true,"homeDistrict":"SUSA"}}""")).andExpect(status().isOk()));
        assertThat(local.path("user").path("legalTypeLabel").asText()).isEqualTo("Fiziki şəxs");
        assertThat(local.path("user").path("localResident").asBoolean()).isTrue();
        assertThat(local.path("user").path("homeDistrictLabel").asText()).isEqualTo("Şuşa");

        // Hüquqi şəxs: VÖEN və şirkət adı məcburidir
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Direktor","email":"sirket.yox@example.az","password":"Parol12345","consent":true,"legal":{"legalType":"COMPANY"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.fieldErrors['legal.voen']").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.fieldErrors['legal.companyName']").exists());
        JsonNode co = json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Rauf Direktor","email":"sirket@example.az","password":"Parol12345","consent":true,"accountType":"ENTREPRENEUR",
                 "legal":{"legalType":"COMPANY","companyName":"Test Aqro MMC","companyForm":"MMC","voen":"1234567890","repPosition":"Direktor"}}"""))
                .andExpect(status().isOk()));
        assertThat(co.path("user").path("legalType").asText()).isEqualTo("COMPANY");
        assertThat(co.path("user").path("displayName").asText()).isEqualTo("Test Aqro MMC (Rauf Direktor)");

        // Xarici vətəndaş: FİN yox, vətəndaşlıq və pasport
        JsonNode fr = json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"John Smith","email":"john@example.com","password":"Parol12345","consent":true,
                 "legal":{"legalType":"FOREIGN","citizenship":"Böyük Britaniya","passportNo":"5123 45678","prefLang":"en"}}"""))
                .andExpect(status().isOk()));
        assertThat(fr.path("user").path("legalTypeLabel").asText()).isEqualTo("Xarici vətəndaş");
        assertThat(fr.path("user").path("passportNo").asText()).isEqualTo("512345678");
        assertThat(fr.path("user").path("prefLang").asText()).isEqualTo("en");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"No Passport","email":"nopass@example.com","password":"Parol12345","consent":true,"legal":{"legalType":"FOREIGN"}}"""))
                .andExpect(status().isBadRequest());
    }

    private JsonNode login(String citizen) throws Exception {
        String state = json(mvc.perform(post("/api/v1/auth/mygov/start")).andExpect(status().isOk())).path("state").asText();
        assertThat(json(mvc.perform(get("/api/v1/auth/mygov/status").param("state", state))).path("status").asText()).isEqualTo("PENDING");
        mvc.perform(post("/api/v1/mock/mygov/approve").contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"" + state + "\",\"citizenId\":\"" + citizen + "\"}")).andExpect(status().isOk());
        JsonNode st = json(mvc.perform(get("/api/v1/auth/mygov/status").param("state", state)));
        assertThat(st.path("status").asText()).isEqualTo("APPROVED");
        return json(mvc.perform(post("/api/v1/auth/mygov/exchange").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + st.path("code").asText() + "\"}")).andExpect(status().isOk()));
    }
}
