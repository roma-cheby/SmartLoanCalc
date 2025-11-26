package ru.Roman.NauJava.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Настройка OpenAPI/Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartLoanCalcOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartLoanCalc API")
                        .version("1.0.0")
                        .description("REST API для работы с кредитными расчётами"))
                .externalDocs(new ExternalDocumentation()
                        .description("Swagger UI")
                        .url("/swagger-ui.html"));
    }
}

