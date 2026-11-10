package cl.rankkings.catalog_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("RankKings - Catalog & Library Service API")
                .version("1.0.0")
                .description("Microservicio CSR de Obras Catalogables y Biblioteca Personal"));
    }
}
