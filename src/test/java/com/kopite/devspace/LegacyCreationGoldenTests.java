package com.kopite.devspace;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LegacyCreationGoldenTests {
    @Test void independentLiteralFingerprintsMatchPreCutoverImplementation() throws Exception {
        var json=JsonMapper.builder().build();
        var cases=json.readTree(Files.readString(Path.of("src/test/resources/category-only/legacy-creations.json")));
        for(var fixture:cases) {
            String resource=fixture.path("resource").asString();
            var parsed=new com.kopite.devspace.compatibility.presentation.LegacyCreateParser(json).parse(
                com.kopite.devspace.compatibility.application.CreationResource.valueOf(resource.toUpperCase(java.util.Locale.ROOT)),fixture.path("requestBody").asString());
            assertEquals(fixture.path("requestHash").asString(),parsed.hash(),resource);
            assertTrue(json.readTree(fixture.path("responseBody").asString()).isObject());
        }
    }
}
