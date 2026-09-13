package com.kopite.devspace.task.presentation;
import com.kopite.devspace.task.presentation.dto.CreateTaskRequest;
import com.kopite.devspace.task.presentation.dto.TaskListResponse;
import com.kopite.devspace.task.presentation.dto.TaskResponse;
import com.kopite.devspace.task.presentation.dto.TaskStatsResponse;

import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration(proxyBeanMethods=false)
public class TaskOpenApiConfiguration {
    @Bean
    OpenApiCustomizer taskContractSchemas() {
        return api -> {
            Schema<?> create=api.getComponents().getSchemas().get("CreateTaskRequest");
            if(create!=null) {
                for(String name:List.of("description","tag")) {
                    Schema<?> property=create.getProperties().get(name);
                    if(property==null || !("string".equals(property.getType())
                        || (property.getTypes()!=null && property.getTypes().contains("string"))))
                        throw new IllegalStateException("Expected Task string schema: "+name);
                    property.setDefault("");
                }
            }
            for(String name:List.of("TaskResponse","TaskListResponse","TaskStatsResponse","Counts")) {
                Schema<?> schema=api.getComponents().getSchemas().get(name);
                if(schema!=null && schema.getProperties()!=null)
                    schema.setRequired(List.copyOf(schema.getProperties().keySet()));
            }
        };
    }
}
