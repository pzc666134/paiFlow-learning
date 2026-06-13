package com.iflytek.astron.workflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI documentation configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paiFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PaiFlow Workflow Engine API")
                        .description("Java workflow engine APIs for workflow execution, protocol management, node debug, and RAG document ingestion.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PaiFlow")
                                .url("https://github.com/iflytek"))
                        .license(new License()
                                .name("Apache-2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }

    @Bean
    public GroupedOpenApi allApiGroup() {
        return GroupedOpenApi.builder()
                .group("all")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    public GroupedOpenApi workflowApiGroup() {
        return GroupedOpenApi.builder()
                .group("workflow")
                .pathsToMatch("/api/workflow/**", "/api/v1/workflow/**", "/workflow/v1/**")
                .build();
    }

    @Bean
    public GroupedOpenApi ragApiGroup() {
        return GroupedOpenApi.builder()
                .group("rag")
                .pathsToMatch("/api/rag/**")
                .build();
    }

    @Bean
    public GroupedOpenApi textEmbeddingApiGroup() {
        return GroupedOpenApi.builder()
                .group("text-embedding")
                .pathsToMatch("/api/rag/texts")
                .build();
    }

    @Bean
    public GroupedOpenApi linkApiGroup() {
        return GroupedOpenApi.builder()
                .group("link")
                .pathsToMatch("/api/v1/tools/**", "/aitools/v1/**")
                .build();
    }
}
