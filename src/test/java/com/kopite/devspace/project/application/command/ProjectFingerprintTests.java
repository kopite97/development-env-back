package com.kopite.devspace.project.application.command;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectFingerprintTests {
    @Test void versionThreeGoldenRetainsPresenceAndRawUuidCase() {
        var omitted=new CreateProjectCommand("Project",null,"Java",null,null,null);
        assertEquals("fc6656fbbaf5f6f99e0d7d191df801b2e03dedd37d0fd1073dca8c42496a06bb",ProjectRequestHash.of(omitted));
        var clear=new CreateProjectCommand("Project",null,"Java",null,null,null,new ProjectCategorySelection(true,null));
        var lower=new CreateProjectCommand("Project",null,"Java",null,null,null,new ProjectCategorySelection(true,"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"));
        var upper=new CreateProjectCommand("Project",null,"Java",null,null,null,new ProjectCategorySelection(true,"AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA"));
        assertEquals(4,java.util.stream.Stream.of(omitted,clear,lower,upper).map(ProjectRequestHash::of).distinct().count());
        assertEquals(lower.values().categoryId(),upper.values().categoryId());
        assertNotEquals(ProjectRequestHash.of(omitted),ProjectRequestHash.of(new CreateProjectCommand(" Project ",null,"Java",null,null,null)));
    }
}
