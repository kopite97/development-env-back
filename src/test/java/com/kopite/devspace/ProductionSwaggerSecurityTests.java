package com.kopite.devspace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles({"test", "prod"})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class ProductionSwaggerSecurityTests {
    private final MockMvc mvc;
    @Autowired
    ProductionSwaggerSecurityTests(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void productionOverridesTestProfileAndKeepsDocumentationProtected() throws Exception {
        for (String path : java.util.List.of("/swagger-ui.html", "/swagger-ui/index.html",
                "/swagger-ui/swagger-ui-bundle.js", "/v3/api-docs", "/v3/api-docs/swagger-config",
                "/api/v1/tasks", "/api/v1/projects")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}
