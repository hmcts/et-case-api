package uk.gov.hmcts.reform.et.syaapi.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfiguration {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info().title("et-sya-api")
                     .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
            .externalDocs(new ExternalDocumentation()
                              .description("README")
                              .url("https://github.com/hmcts/et-sya-api"));
    }

}
