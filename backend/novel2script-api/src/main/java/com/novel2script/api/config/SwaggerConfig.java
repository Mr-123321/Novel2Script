package com.novel2script.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration for the Novel2Script API.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI novel2ScriptOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Novel2Script API")
                        .description("AI-driven novel-to-script conversion system — REST API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Novel2Script Team")
                                .email("dev@novel2script.com"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
