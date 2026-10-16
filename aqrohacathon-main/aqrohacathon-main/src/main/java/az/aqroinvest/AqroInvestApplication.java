package az.aqroinvest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Əkinçi 2.0 - işğaldan azad edilmiş ərazilərdə aqrar investisiya müsabiqələri portalı.
 * Modul monolit arxitektura: hər paket (plot, competition, application, integration ...) ayrıca
 * sərhədli kontekstdir və gələcəkdə mikroxidmətə ayrıla bilər.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class AqroInvestApplication {
    public static void main(String[] args) {
        SpringApplication.run(AqroInvestApplication.class, args);
    }
}
