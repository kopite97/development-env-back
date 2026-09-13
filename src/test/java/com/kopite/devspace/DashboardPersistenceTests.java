package com.kopite.devspace;
import com.kopite.devspace.dashboard.domain.*;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DashboardPersistenceTests {
    @Autowired UserWorkspaceCreationService users;
    @Autowired HomeDashboardRepository dashboards;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired DataSource dataSource;
    UUID workspace() {return users.createOrReuse("dashboard-persistence",UUID.randomUUID().toString(),"Owner").workspace().getId();}
    TransactionTemplate tx() {return new TransactionTemplate(manager);}
    @Test void jsonRoundTripOrderOmissionAndEmpty() {
        UUID w=workspace();
        tx().executeWithoutResult(s->assertTrue(dashboards.find(w).isEmpty()));
        assertEquals(0L,jdbc.queryForObject("select count(*) from dashboards where workspace_id=?",Long.class,w));
        var defaults=HomeDashboard.defaults();
        tx().executeWithoutResult(s->{dashboards.insert(HomeDashboard.create(w,defaults,Instant.now()));dashboards.flush();em.clear();
            var saved=dashboards.find(w).orElseThrow();assertEquals(defaults,saved.getWidgets());assertEquals(1,saved.getRevision());
            assertEquals(saved.getCreatedAt(),saved.getUpdatedAt());saved.validateStored();
            assertThrows(UnsupportedOperationException.class,()->saved.getWidgets().clear());
        });
        assertFalse(jdbc.queryForObject("select jsonb_exists(widgets->0,'limit') or jsonb_exists(widgets->0,'projectId') from dashboards where workspace_id=?",Boolean.class,w));
        tx().executeWithoutResult(s->{var d=dashboards.lock(w).orElseThrow();d.replace(1,defaults.reversed(),Instant.now());});
        tx().executeWithoutResult(s->{var d=dashboards.find(w).orElseThrow();assertEquals(defaults.reversed(),d.getWidgets());d.replace(2,List.of(),Instant.now());});
        assertEquals("[]",jdbc.queryForObject("select widgets::text from dashboards where workspace_id=?",String.class,w));
        assertEquals(0L,jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,w));
    }
    @Test void domainMatrixAndValidation() {
        for(String type:List.of("overview","board","deploy","links","journal","milestone"))
            for(String size:List.of("small","medium","wide"))
                for(String scope:List.of("all","unity","server")) {
                    var v=new DashboardWidget(" id ",type," title ",scope,size,null,null);
                    assertEquals("id",v.id());assertEquals("title",v.title());assertEquals(scope,v.scope());
                    if(!Set.of("deploy","links").contains(type)) {
                        assertEquals("all",new DashboardWidget("x",type,"t",scope,size,UUID.randomUUID(),20).scope());
                        assertEquals(1,new DashboardWidget("x",type,"t",scope,size,null,1).limit());
                    } else {
                        assertThrows(DashboardValidationException.class,()->new DashboardWidget("x",type,"t",scope,size,UUID.randomUUID(),null));
                        assertThrows(DashboardValidationException.class,()->new DashboardWidget("x",type,"t",scope,size,null,1));
                    }
                }
        var v=new DashboardWidget("a","board","😀".repeat(24),"all","wide",null,null);
        assertThrows(DashboardValidationException.class,()->new DashboardWidget("a","board","😀".repeat(25),"all","wide",null,null));
        for(String field:List.of("", " ", "\u2003")) {
            assertThrows(DashboardValidationException.class,()->new DashboardWidget(field,"board","t","all","wide",null,null));
            assertThrows(DashboardValidationException.class,()->new DashboardWidget("x","board",field,"all","wide",null,null));
        }
        for(int limit:new int[]{-1,0,21,Integer.MAX_VALUE})
            assertThrows(DashboardValidationException.class,()->new DashboardWidget("x","board","t","all","wide",null,limit));
        assertThrows(DashboardValidationException.class,()->new DashboardWidget("x","BOARD","t","all","wide",null,null));
        assertThrows(DashboardValidationException.class,()->new DashboardWidget("x","board","t","ALL","wide",null,null));
        assertThrows(DashboardValidationException.class,()->new DashboardWidget("x","board","t","all","large",null,null));
        assertThrows(DashboardValidationException.class,()->HomeDashboard.validate(List.of(v,new DashboardWidget(" a ","board","t","all","wide",null,null))));
        assertEquals(2,HomeDashboard.validate(List.of(v,new DashboardWidget("A","board","t","all","wide",null,null))).size());
        assertThrows(DashboardValidationException.class,()->HomeDashboard.validate(null));
        assertThrows(DashboardValidationException.class,()->HomeDashboard.validate(Arrays.asList(v,null)));
        var d=HomeDashboard.create(UUID.randomUUID(),List.of(v),Instant.now());
        assertThrows(DashboardConflictException.class,()->d.replace(0,List.of(),Instant.now()));
        assertThrows(DashboardValidationException.class,()->HomeDashboard.validateRevision(-1));
        assertThrows(DashboardValidationException.class,()->HomeDashboard.validateRevision(HomeDashboard.MAX_REVISION+1));
    }
    @Test void databaseConstraintsAndOverflow() {
        UUID w=workspace();
        tx().executeWithoutResult(s->dashboards.insert(HomeDashboard.create(w,List.of(),Instant.now())));
        for(String assignment:List.of("revision=0","revision=9007199254740992","schema_version=2","schema_version=null","widgets='{}'","widgets=null","dashboard_key='other'","created_at=null"))
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update dashboards set "+assignment+" where workspace_id=?",w),assignment);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update dashboards set workspace_id=? where workspace_id=?",UUID.randomUUID(),w));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into dashboards select * from dashboards where workspace_id=?",w));
        jdbc.update("update dashboards set revision=? where workspace_id=?",HomeDashboard.MAX_REVISION,w);
        tx().executeWithoutResult(s->assertThrows(DashboardConflictException.class,()->dashboards.find(w).orElseThrow().replace(HomeDashboard.MAX_REVISION,List.of(),Instant.now())));
    }
    @Test void populatedV11UpgradePreservesAllRowsAndChecksums() {
        String schema="dashboard_upgrade_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("11").load().migrate();
        UUID u=UUID.randomUUID(),w=UUID.randomUUID(),p=UUID.randomUUID();
        jdbc.update("insert into "+schema+".users values(?,'Existing',now(),now(),null)",u);
        jdbc.update("insert into "+schema+".workspaces values(?,?,'Existing',7,42,now(),now())",w,u);
        jdbc.update("insert into "+schema+".auth_identities(issuer,subject,user_id) values('upgrade','subject',?)",u);
        jdbc.update("insert into "+schema+".projects(id,workspace_id,name,scope,stack,created_at,updated_at) values(?,?,'Existing','server','Java',now(),now())",p,w);
        jdbc.update("insert into "+schema+".tasks(id,workspace_id,project_id,title,created_at,updated_at) values(?,?,?,'Existing',now(),now())",UUID.randomUUID(),w,p);
        jdbc.update("insert into "+schema+".journals(id,workspace_id,project_id,title,body,entry_date,created_at,updated_at) values(?,?,?,'Existing','Body','2024-02-29',now(),now())",UUID.randomUUID(),w,p);
        jdbc.update("insert into "+schema+".milestones(id,workspace_id,project_id,title,created_at,updated_at) values(?,?,?,'Existing',now(),now())",UUID.randomUUID(),w,p);
        jdbc.update("insert into "+schema+".link_collections values(?,7)",w);
        jdbc.update("insert into "+schema+".links(id,workspace_id,label,url,position,created_at,updated_at) values(?,?,'Existing','https://example.com',0,now(),now())",UUID.randomUUID(),w);
        for(String resource:List.of("project","task","journal","milestone","link"))
            jdbc.update("insert into "+schema+"."+resource+"_create_idempotency(workspace_id,method,path,key,request_hash,response_status,response_body,created_at,expires_at) values(?,'POST',?,'existing','hash',201,'{}',now(),now()+interval '24 hours')",w,"/api/v1/"+resource+"s");
        var tables=jdbc.queryForList("select table_name from information_schema.tables where table_schema=? and table_name<>'flyway_schema_history'",String.class,schema);
        Map<String,List<Map<String,Object>>> before=new HashMap<>();
        tables.forEach(t->before.put(t,jdbc.queryForList("select * from "+schema+"."+t)));
        var checksums=jdbc.queryForList("select version,checksum from "+schema+".flyway_schema_history order by installed_rank");
        var flyway=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load();
        assertEquals(1,flyway.migrate().migrationsExecuted);flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
        tables.forEach(t->assertEquals(before.get(t),jdbc.queryForList("select * from "+schema+"."+t),t));
        assertEquals(checksums,jdbc.queryForList("select version,checksum from "+schema+".flyway_schema_history where version<>'12' or version is null order by installed_rank"));
        assertEquals(0L,jdbc.queryForObject("select count(*) from "+schema+".dashboards",Long.class));
    }
}
