package com.kopite.devspace.journal.presentation;
import com.kopite.devspace.journal.presentation.dto.DeleteJournalResponse;
import com.kopite.devspace.journal.presentation.dto.JournalListResponse;
import com.kopite.devspace.journal.presentation.dto.JournalResponse;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
@Configuration(proxyBeanMethods=false)
public class JournalOpenApiConfiguration {
    @Bean OpenApiCustomizer journalContractSchemas() {
        return api -> {
            for(String name:List.of("JournalResponse","JournalListResponse","DeleteJournalResponse")) {
                Schema<?> schema=api.getComponents().getSchemas().get(name);
                if(schema!=null && schema.getProperties()!=null) schema.setRequired(List.copyOf(schema.getProperties().keySet()));
            }
            var path=api.getPaths().get("/api/v1/journals");
            if(path!=null && path.getGet()!=null) path.getGet().getParameters().stream()
                .filter(p->"query".equals(p.getName())).forEach(p->p.getSchema().setDefault(""));
        };
    }
}
