package az.aqroinvest;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Xəta cavabları və statik faylların keşi: istifadəçi xam JSON və ya "Daxili xəta" görmür. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebBasicsTest {
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("Olmayan səhifə dizaynlı 404 HTML qaytarır, API isə JSON")
    void notFound() throws Exception {
        String html = mvc.perform(get("/sehe.html").accept(MediaType.TEXT_HTML)).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML)).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Bu səhifə tapılmadı");
        mvc.perform(get("/js/yoxdur.js")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        // Uzantısız naməlum ünvan brauzerdə də 404 səhifəsidir; API isə əvvəlki kimi 401 JSON qaytarır
        assertThat(mvc.perform(get("/yoxdur-sehife").accept(MediaType.TEXT_HTML)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString()).contains("Bu səhifə tapılmadı");
        mvc.perform(get("/api/v1/me/watches").accept(MediaType.TEXT_HTML, MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Naməlum istifadə növü, səhv JSON və çatışmayan parametr: 500 deyil, izahlı 400")
    void badRequests() throws Exception {
        mvc.perform(post("/api/v1/simulate").contentType(MediaType.APPLICATION_JSON).content("{\"plotId\":1,\"useCase\":\"XXX\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_VALUE"))
                .andExpect(jsonPath("$.fieldErrors.useCase").exists());
        mvc.perform(post("/api/v1/simulate").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
        mvc.perform(get("/api/v1/track")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
    }

    @Test
    @DisplayName("Statik fayllar keşlənir: vendor uzun müddət, CSS və JS hər dəfə Last-Modified ilə yoxlanır")
    void staticCache() throws Exception {
        mvc.perform(get("/vendor/chart.umd.js")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "public, max-age=604800"));
        mvc.perform(get("/css/app.css")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-cache"));
        mvc.perform(get("/api/v1/plots")).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }
}
