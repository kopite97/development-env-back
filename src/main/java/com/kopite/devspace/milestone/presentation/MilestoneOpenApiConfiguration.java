package com.kopite.devspace.milestone.presentation;
import com.kopite.devspace.milestone.presentation.dto.DeleteMilestoneResponse;
import com.kopite.devspace.milestone.presentation.dto.MilestoneListResponse;
import com.kopite.devspace.milestone.presentation.dto.MilestoneResponse;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
@Configuration(proxyBeanMethods=false)
public class MilestoneOpenApiConfiguration {
    @Bean OpenApiCustomizer milestoneContractSchemas() {
        return api -> {
            for(String name:List.of("MilestoneResponse","MilestoneListResponse","DeleteMilestoneResponse")) {
                Schema<?> schema=api.getComponents().getSchemas().get(name);
                if(schema!=null && schema.getProperties()!=null) schema.setRequired(List.copyOf(schema.getProperties().keySet()));
            }
        };
    }
}
