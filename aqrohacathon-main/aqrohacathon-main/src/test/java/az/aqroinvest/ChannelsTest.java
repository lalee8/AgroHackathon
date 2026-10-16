package az.aqroinvest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canlı kanal (SSE), Telegram və peyk monitorinqi üçün giriş qaydaları. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChannelsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired az.aqroinvest.plot.PlotRepository plots;

    @Test
    @DisplayName("SSE: bilet yalnız daxil olmuş istifadəçiyə verilir, saxta biletlə axın açılmır")
    void liveStreamRequiresTicket() throws Exception {
        mvc.perform(post("/api/v1/live/ticket")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/live/stream").param("ticket", "saxta-bilet")).andExpect(status().isUnauthorized());
        String investor = TestSupport.login(mvc, mapper, "investor@aqroinvest.az", "Investor123!");
        mvc.perform(post("/api/v1/live/ticket").header("Authorization", investor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").isString());
    }

    @Test
    @DisplayName("Telegram: token yoxdursa kanal söndürülüb, qoşulma 503 qaytarır")
    void telegramDisabledWithoutToken() throws Exception {
        String investor = TestSupport.login(mvc, mapper, "investor@aqroinvest.az", "Investor123!");
        mvc.perform(get("/api/v1/telegram/status").header("Authorization", investor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.linked").value(false));
        mvc.perform(post("/api/v1/telegram/link").header("Authorization", investor))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Peyk monitorinqi yalnız admin üçündür və bütün sahələri əhatə edir")
    void monitoringIsAdminOnly() throws Exception {
        String investor = TestSupport.login(mvc, mapper, "investor@aqroinvest.az", "Investor123!");
        mvc.perform(get("/api/v1/admin/monitoring").header("Authorization", investor)).andExpect(status().isForbidden());
        String admin = TestSupport.login(mvc, mapper, "admin@aqroinvest.az", "Admin123!");
        mvc.perform(get("/api/v1/admin/monitoring").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize((int) plots.count())));
    }
}
