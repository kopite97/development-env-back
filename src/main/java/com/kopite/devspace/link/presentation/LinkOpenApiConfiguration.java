package com.kopite.devspace.link.presentation;
import com.kopite.devspace.link.presentation.dto.CreateLinkRequest;
import com.kopite.devspace.link.presentation.dto.DeleteLinkResponse;
import com.kopite.devspace.link.presentation.dto.LinkListResponse;
import com.kopite.devspace.link.presentation.dto.LinkMutationResponse;
import com.kopite.devspace.link.presentation.dto.LinkResponse;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
@Configuration(proxyBeanMethods=false)
public class LinkOpenApiConfiguration {
    @Bean OpenApiCustomizer linkContractSchemas() {
        return api -> {
            Schema<?> create=api.getComponents().getSchemas().get("CreateLinkRequest");
            if(create!=null) {
                Schema<?> description=create.getProperties().get("description");
                if(description==null || !("string".equals(description.getType())
                    || (description.getTypes()!=null && description.getTypes().contains("string"))))
                    throw new IllegalStateException("Expected a string schema for CreateLinkRequest.description");
                description.setDefault("");
            }
            for(String name:List.of("LinkResponse","LinkListResponse","DeleteLinkResponse","LinkMutationResponse")) {
                Schema<?> schema=api.getComponents().getSchemas().get(name);
                if(schema!=null && schema.getProperties()!=null) schema.setRequired(List.copyOf(schema.getProperties().keySet()));
            }
        };
    }
}
