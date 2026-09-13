package com.kopite.devspace.project.presentation;
import com.kopite.devspace.project.presentation.dto.CreateProjectRequest;

import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProjectOpenApiConfiguration {
    @Bean
    OpenApiCustomizer projectStringDefaults() {
        // An empty @Schema.defaultValue is treated as unspecified by the annotation processor.
        return api -> {
            Schema<?> create = api.getComponents().getSchemas().get("CreateProjectRequest");
            if (create != null) {
                for (String field : java.util.List.of("subtitle", "currentMilestone", "repositoryUrl")) {
                    Schema<?> property = create.getProperties().get(field);
                    if (property == null || !("string".equals(property.getType())
                            || (property.getTypes() != null && property.getTypes().contains("string")))) {
                        throw new IllegalStateException("Expected a string schema for CreateProjectRequest." + field);
                    }
                    property.setDefault("");
                }
            }
        };
    }
}
