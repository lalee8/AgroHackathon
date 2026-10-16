package az.aqroinvest.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Əkinçi 2.0 API")
                        .version("v1")
                        .description("""
                                İşğaldan azad edilmiş ərazilər üçün Aqrar İnvestisiya Müsabiqəsi Portalının REST API-si.
                                
                                * **İctimai API** - sahələr, müsabiqələr, tövsiyə, ROI, status yoxlaması
                                * **İnvestor API** (JWT) - elektron müraciət, sənədlər, bildirişlər
                                * **Admin / Ekspert API** (JWT + rol) - idarəetmə, qiymətləndirmə, hesabatlar
                                * **EKTİS inteqrasiya API** (X-API-Key) - sistemlərarası məlumat mübadiləsi
                                
                                Demo hesablar: `admin@aqroinvest.az / Admin123!`, `investor@aqroinvest.az / Investor123!`""")
                        .contact(new Contact().name("Əkinçi 2.0 komandası"))
                        .license(new License().name("Hakaton prototipi")))
                .components(new Components()
                        .addSecuritySchemes("bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes("apiKey", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name("X-API-Key")));
    }
}
