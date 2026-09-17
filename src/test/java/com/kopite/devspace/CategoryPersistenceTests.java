package com.kopite.devspace;

import com.kopite.devspace.projectcategory.domain.*;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Import(TestcontainersConfiguration.class)
class CategoryPersistenceTests {
    @Autowired UserWorkspaceCreationService users;
    @Autowired ProjectCategoryRepository categories;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager manager;

    @Test void normalizedNamesAndRevisionInvariants() {
        assertEquals("Caf\u00e9",new CategoryName(" Cafe\u0301 ").value());
        assertNotEquals(new CategoryName("Tools"),new CategoryName("tools"));
        assertEquals("a  b",new CategoryName("a  b").value());
        assertEquals("\u2003a\u2003",new CategoryName("\u2003a\u2003").value());
        assertEquals(100,new CategoryName("\ud83d\ude00".repeat(50)).value().length());
        for(String s:new String[]{"", " \t", "\u2003", "x".repeat(101),"\ud800","\udc00","x\ud800a"})
            assertThrows(CategoryValidationException.class,()->new CategoryName(s));
        assertThrows(CategoryValidationException.class,()->new CategoryName(null));
        var c=ProjectCategory.create(UUID.randomUUID(),new CategoryName("n"),Instant.now());
        assertThrows(CategoryConflictException.class,()->c.rename(2,new CategoryName("other"),Instant.now()));
        c.rename(1,new CategoryName("n"),Instant.now()); assertEquals(2,c.getRevision());
    }

    @Test void ownershipUniquenessAndRestrictiveForeignKey() {
        var a=users.createOrReuse("category-db",UUID.randomUUID().toString(),"Owner");
        var b=users.createOrReuse("category-db",UUID.randomUUID().toString(),"Other");
        var tx=new TransactionTemplate(manager);
        UUID w=a.workspace().getId();
        var c=tx.execute(s->{var created=categories.save(ProjectCategory.create(w,new CategoryName("Tools"),Instant.now()));categories.flush();return created;});
        tx.executeWithoutResult(s->{assertTrue(categories.findOwned(b.workspace().getId(),c.getId()).isEmpty());assertEquals("Tools",categories.findOwned(w,c.getId()).orElseThrow().getName());});
        assertThrows(CategoryConflictException.class,()->tx.executeWithoutResult(s->{categories.save(ProjectCategory.create(w,new CategoryName("Tools"),Instant.now()));categories.flush();}));
        tx.executeWithoutResult(s->{categories.save(ProjectCategory.create(w,new CategoryName("tools"),Instant.now()));categories.flush();});
        UUID p=UUID.randomUUID();
        jdbc.update("insert into projects(id,workspace_id,name,stack,status,category_id,created_at,updated_at) values(?,?,'Same','Java','archived',?,now(),now())",p,w,c.getId());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("update projects set workspace_id=? where id=?",b.workspace().getId(),p));
        assertThrows(CategoryConflictException.class,()->tx.executeWithoutResult(s->categories.delete(categories.lockOwned(w,c.getId()).orElseThrow())));
        assertEquals(c.getId(),jdbc.queryForObject("select category_id from projects where id=?",UUID.class,p));
        jdbc.update("update projects set category_id=null where id=?",p);
        tx.executeWithoutResult(s->categories.delete(categories.lockOwned(w,c.getId()).orElseThrow()));
        assertEquals(0L,jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,w));
    }

    @Test void populatedV12UpgradePreservesEveryOldColumnAndReplay() throws Exception {
        String schema="category_upgrade_"+UUID.randomUUID().toString().replace("-","");
        var old=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("12").load(); old.migrate();
        try(var connection=dataSource.getConnection()) {
            String originalSchema=connection.getSchema();
            try {
            connection.setSchema(schema);
            var db=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
            for(int i=0;i<2;i++) {
                UUID u=UUID.randomUUID(),w=UUID.randomUUID();
                db.update("insert into users values(?,'Owner',now(),now(),null)",u);
                db.update("insert into auth_identities values('category-upgrade',?,?)",u.toString(),u);
                db.update("insert into workspaces values(?,?,'Workspace',7,42,now(),now())",w,u);
                for(String scope:List.of("unity","server")) for(String status:List.of("active","archived")) {
                    UUID p=UUID.randomUUID();
                    db.update("insert into projects(id,workspace_id,name,scope,stack,status,revision,created_at,updated_at) values(?,?,'Project',?,'Java',?,5,now(),now())",p,w,scope,status);
                    db.update("insert into tasks(id,workspace_id,project_id,title,created_at,updated_at) values(?,?,?,'Task',now(),now())",UUID.randomUUID(),w,p);
                    db.update("insert into journals(id,workspace_id,project_id,title,body,entry_date,created_at,updated_at) values(?,?,?,'Journal','Body',current_date,now(),now())",UUID.randomUUID(),w,p);
                    db.update("insert into milestones(id,workspace_id,project_id,title,created_at,updated_at) values(?,?,?,'Milestone',now(),now())",UUID.randomUUID(),w,p);
                    String body="{\"id\":\""+p+"\",\"revision\":1,\"createdAt\":\"2026-09-14T00:00:00Z\",\"updatedAt\":\"2026-09-14T00:00:00Z\",\"name\":\"Project\",\"subtitle\":\"\",\"scope\":\"unity\",\"stack\":\"Java\",\"progress\":0,\"currentMilestone\":\"\",\"repositoryUrl\":\"\",\"status\":\"active\",\"colorToken\":\"unity\"}";
                    db.update("insert into project_create_idempotency values(?,'POST','/api/v1/projects',?,'legacy-hash',201,?,now()-interval '2 days',now()+cast(? as interval))",w,p.toString(),body,status.equals("active")?"1 day":"-1 day");
                }
                db.update("insert into link_collections values(?,3)",w);
                db.update("insert into links(id,workspace_id,label,url,position,created_at,updated_at) values(?,?,'Link','https://example.com',0,now(),now())",UUID.randomUUID(),w);
                db.update("insert into dashboards values(?,'home',1,3,'[]',now(),now())",w);
            }
            var tables=List.of("users","auth_identities","workspaces","projects","tasks","journals","milestones","link_collections","links","dashboards","project_create_idempotency");
            Map<String,List<String>> before=new LinkedHashMap<>();
            for(String t:tables) before.put(t,db.queryForList("select row_to_json(t)::text from "+t+" t order by row_to_json(t)::text",String.class));
            var checksums=db.queryForList("select version,checksum from flyway_schema_history order by installed_rank");
            var next=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("14").load(); next.migrate();next.validate();
            for(String t:tables) {
                String projection=t.equals("projects")?"(to_jsonb(t)-'category_id')::text":"row_to_json(t)::text";
                var after=db.queryForList("select "+projection+" from "+t+" t",String.class);
                var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
                assertEquals(before.get(t).stream().map(mapper::readTree).collect(java.util.stream.Collectors.toSet()),after.stream().map(mapper::readTree).collect(java.util.stream.Collectors.toSet()),t);
            }
            assertEquals(checksums,db.queryForList("select version,checksum from flyway_schema_history where installed_rank<=12 order by installed_rank"));
            assertEquals(0L,db.queryForObject("select count(*) from projects where category_id is not null",Long.class));
            assertEquals(0L,db.queryForObject("select count(*) from project_categories",Long.class));
            java.nio.file.Path out=java.nio.file.Path.of(".gradle/project-category-validation/migration");java.nio.file.Files.createDirectories(out);
            java.nio.file.Files.writeString(out.resolve("populated-v12.txt"),"V12 -> V14 validated; 2 workspaces, 8 Projects, all dependent tables and expired/unexpired replay rows preserved.\n"+checksums);
            } finally {
                // Hikari cannot restore a schema when its configured default is null.
                // Never return a connection pointing at this isolated migration fixture.
                connection.setSchema(originalSchema);
                assertEquals(originalSchema,connection.getSchema());
            }
        }
    }
}
