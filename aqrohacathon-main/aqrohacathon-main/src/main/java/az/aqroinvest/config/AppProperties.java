package az.aqroinvest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        Security security,
        Storage storage,
        Competition competition,
        Seed seed,
        Ektis ektis,
        Imagery imagery,
        Telegram telegram,
        Ai ai
) {
    /**
     * AI məsləhətçi. provider: auto (Gemini açarı varsa Gemini, Claude açarı varsa Claude), gemini, claude və ya offline.
     * Açar yoxdursa məsləhətçi oflayn (qaydalara əsaslanan) rejimdə işləyir. apiKey, model, effort, baseUrl Claude üçündür.
     */
    public record Ai(String provider, String apiKey, String model, String effort, int maxTokens, boolean fallbacks,
                     int requestsPerMinute, String baseUrl, Gemini gemini) {
        public record Gemini(String apiKey, String model, String fallbackModel, String thinking, String baseUrl) {
            public boolean enabled() {
                return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
            }
        }

        /** İşləyən provayder: "gemini", "claude" və ya null (oflayn). */
        public String active() {
            boolean g = gemini != null && gemini.enabled(), c = apiKey != null && !apiKey.isBlank();
            return switch (provider == null ? "auto" : provider.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "gemini" -> g ? "gemini" : null;
                case "claude", "anthropic" -> c ? "claude" : null;
                case "offline", "none" -> null;
                default -> g ? "gemini" : c ? "claude" : null;
            };
        }

        public boolean enabled() {
            return active() != null;
        }
    }

    public record Telegram(String botToken, String apiUrl) {
        public boolean enabled() {
            return botToken != null && !botToken.isBlank();
        }
    }

    public record Security(String jwtSecret, long jwtTtlMinutes, int maxFailedLogins, long lockMinutes, Integer loginsPerMinute) {
        /** IP üzrə dəqiqədə giriş cəhdi limiti (standart 20). */
        public int loginLimit() { return loginsPerMinute == null || loginsPerMinute < 1 ? 20 : loginsPerMinute; }
    }

    public record Storage(String uploadDir, List<String> allowedTypes) {}

    public record Competition(boolean fourEyes) {}

    public record Seed(boolean enabled) {}

    public record Imagery(boolean enabled, String cacheDir, String landsatUrl, String sentinelWms, String sentinelLiveUrl, Integer liveRefreshHours) {
        public int refreshHours() { return liveRefreshHours == null || liveRefreshHours < 1 ? 12 : liveRefreshHours; }
    }

    public record Ektis(String baseUrl, String apiKey, String hmacSecret, long outboxIntervalMs,
                        int maxAttempts, Mock mock) {
        public record Mock(double failureRate) {}
    }
}
