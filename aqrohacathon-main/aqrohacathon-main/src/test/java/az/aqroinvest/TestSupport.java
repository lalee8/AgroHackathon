package az.aqroinvest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Testlər üçün köməkçi: giriş, JSON oxuma. */
public final class TestSupport {
    private TestSupport() {}

    public static String login(MockMvc mvc, ObjectMapper mapper, String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).get("token").asText();
    }

    public static String register(MockMvc mvc, ObjectMapper mapper, String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("fullName", "Test İnvestor", "email", email,
                                "password", "Test12345", "phone", "+994501112233", "fin", "AB12CD3", "consent", true))))
                .andReturn().getResponse().getContentAsString();
        JsonNode n = mapper.readTree(body);
        return "Bearer " + n.get("token").asText();
    }

    public static final byte[] PDF = "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n".getBytes();
}
