package com.kopite.devspace;
import com.kopite.devspace.milestone.domain.Milestone;
import com.kopite.devspace.milestone.domain.MilestoneConflictException;
import com.kopite.devspace.milestone.domain.MilestoneRepository;
import com.kopite.devspace.milestone.domain.MilestoneValidationException;
import com.kopite.devspace.milestone.domain.MilestoneValues;

import com.kopite.devspace.user.application.UserWorkspaceCreationService;
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
import java.time.LocalDate;
import java.util.TimeZone;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MilestonePersistenceTests {
    private final UserWorkspaceCreationService users;
    private final MilestoneRepository milestones;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DataSource dataSource;
    @Autowired
    MilestonePersistenceTests(UserWorkspaceCreationService users,MilestoneRepository milestones,JdbcTemplate jdbc,
                            PlatformTransactionManager manager,DataSource dataSource) {
        this.users=users; this.milestones=milestones; this.jdbc=jdbc;
        this.transaction=new TransactionTemplate(manager); this.dataSource=dataSource;
    }
    private UUID workspace() { return users.createOrReuse("milestone-persistence",UUID.randomUUID().toString(),"Owner").workspace().getId(); }
    private UUID project(UUID workspace) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into projects(id,workspace_id,name,scope,stack,created_at,updated_at) values(?,?,'Project','server','Java',now(),now())",id,workspace);
        return id;
    }
    private MilestoneValues values(UUID project) { return new MilestoneValues(" Title ",project,LocalDate.of(2024,2,29),false); }

    @Test
    void datesRoundTripAcrossTimezonesAndAuditRevisionDeleteRollbackRemainIndependent() {
        UUID workspace=workspace(); UUID project=project(workspace);
        var projectBefore=jdbc.queryForObject("select row_to_json(p)::text from projects p where id=?",String.class,project);
        var sibling=transaction.execute(s->milestones.save(Milestone.create(workspace,values(project),Instant.now())));
        assertNotNull(sibling);
        var siblingBefore=jdbc.queryForObject("select row_to_json(m)::text from milestones m where id=?",String.class,sibling.getId());
        Instant created=Instant.parse("2026-09-13T03:00:00.123456Z");
        TimeZone original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            Milestone saved=transaction.execute(s->milestones.save(Milestone.create(workspace,values(project),created)));
            assertNotNull(saved);
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            transaction.executeWithoutResult(s->{
                var milestone=milestones.lockOwned(workspace,saved.getId()).orElseThrow();
                assertEquals(LocalDate.of(2024,2,29),milestone.getDueDate());
                assertEquals(created,milestone.getCreatedAt()); assertEquals(created,milestone.getUpdatedAt());
                assertEquals("Title",milestone.getTitle()); assertFalse(milestone.isCompleted());
                assertTrue(milestones.findOwned(UUID.randomUUID(),milestone.getId()).isEmpty());
                milestone.update(1,milestone.values(),created.plusSeconds(1));
                assertEquals(2,milestone.getRevision());
                assertThrows(MilestoneConflictException.class,()->milestone.update(1,milestone.values(),created));
                milestone.update(2,new MilestoneValues("Changed",project,null,true),created.plusSeconds(2));
            });
            assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(s->{
                milestones.delete(milestones.lockOwned(workspace,saved.getId()).orElseThrow());
                throw new IllegalStateException("rollback deletion");
            }));
            transaction.executeWithoutResult(s->{
                var milestone=milestones.lockOwned(workspace,saved.getId()).orElseThrow();
                assertEquals(created,milestone.getCreatedAt()); assertEquals(created.plusSeconds(2),milestone.getUpdatedAt());
                assertNull(milestone.getDueDate()); assertTrue(milestone.isCompleted()); assertEquals(workspace,milestone.getWorkspaceId());
                milestone.checkRevision(3); milestones.delete(milestone);
            });
            assertEquals(0L,jdbc.queryForObject("select count(*) from milestones where id=?",Long.class,saved.getId()));
            assertEquals(projectBefore,jdbc.queryForObject("select row_to_json(p)::text from projects p where id=?",String.class,project));
            assertEquals(siblingBefore,jdbc.queryForObject("select row_to_json(m)::text from milestones m where id=?",String.class,sibling.getId()));
        } finally { TimeZone.setDefault(original); }
    }

    @Test
    void domainEnforcesUtf16WhitespaceAndNullableCalendarDates() {
        UUID project=UUID.randomUUID(); String emoji="\uD83D\uDE00";
        assertEquals(200,new MilestoneValues(emoji.repeat(100),project,null,false).title().length());
        assertThrows(MilestoneValidationException.class,()->new MilestoneValues(emoji.repeat(101),project,null,false));
        for(String blank:new String[]{null,""," \t\n","\u2003"})
            assertThrows(MilestoneValidationException.class,()->new MilestoneValues(blank,project,null,false));
        assertThrows(MilestoneValidationException.class,()->new MilestoneValues("title",null,null,false));
        for(String invalid:new String[]{"","2023-02-29","2024-02-30","2024-13-01","0000-01-01","10000-01-01","2024-2-01","2024-02-29T00:00:00Z"," 2024-02-29"})
            assertThrows(MilestoneValidationException.class,()->MilestoneValues.date(invalid,"dueDate"),invalid);
        assertEquals(LocalDate.of(2024,2,29),MilestoneValues.date("2024-02-29","dueDate"));
        for(LocalDate date:new LocalDate[]{LocalDate.of(1,1,1),LocalDate.of(9999,12,31)})
            assertEquals(date,new MilestoneValues("title",project,date,true).dueDate());
        assertThrows(MilestoneValidationException.class,()->new MilestoneValues("title",project,LocalDate.of(0,1,1),false));
    }

    @Test
    void databaseConstraintsAreComplementaryAndRejectForeignProjectAndRevisionOverflow() {
        UUID workspace=workspace(); UUID project=project(workspace); UUID id=UUID.randomUUID();
        jdbc.update("insert into milestones(id,workspace_id,project_id,title,due_date,created_at,updated_at) values(?,?,?,'Title','2024-02-29',now(),now())",id,workspace,project);
        assertEquals(1L,jdbc.queryForObject("select revision from milestones where id=?",Long.class,id));
        for(String assignment:new String[]{"title=null","title=' '","title=repeat('x',201)","completed=null",
            "due_date='0001-01-01 BC'","due_date='10000-01-01'","revision=0","revision=9007199254740992"})
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update milestones set "+assignment+" where id=?",id),assignment);
        UUID foreign=project(workspace());
        for(UUID target:new UUID[]{foreign,UUID.randomUUID()})
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update milestones set project_id=? where id=?",target,id));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("delete from projects where id=?",project));
        jdbc.update("update milestones set revision=? where id=?",Milestone.MAX_REVISION,id);
        transaction.executeWithoutResult(s->{
            var milestone=milestones.lockOwned(workspace,id).orElseThrow();
            assertThrows(MilestoneConflictException.class,()->milestone.update(Milestone.MAX_REVISION,milestone.values(),Instant.now()));
            milestone.checkRevision(Milestone.MAX_REVISION); milestones.delete(milestone);
        });
    }

    @Test
    void upgradesPopulatedV7WithoutRewritingDataOrChecksums() {
        String schema="milestone_upgrade_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("7").load().migrate();
        UUID user=UUID.randomUUID(); UUID workspace=UUID.randomUUID(); UUID project=UUID.randomUUID();
        jdbc.update("insert into "+schema+".users values(?,'Existing',now(),now(),null)",user);
        jdbc.update("insert into "+schema+".workspaces values(?,?,'Existing',7,42,now(),now())",workspace,user);
        jdbc.update("insert into "+schema+".projects(id,workspace_id,name,scope,stack,created_at,updated_at) values(?,?,'Existing','server','Java',now(),now())",project,workspace);
        jdbc.update("insert into "+schema+".tasks(id,workspace_id,project_id,title,created_at,updated_at) values(?,?,?,'Existing',now(),now())",UUID.randomUUID(),workspace,project);
        jdbc.update("insert into "+schema+".journals(id,workspace_id,project_id,title,body,entry_date,created_at,updated_at) values(?,?,?,'Existing','Body','2024-02-29',now(),now())",UUID.randomUUID(),workspace,project);
        jdbc.update("insert into "+schema+".auth_identities(issuer,subject,user_id) values('upgrade','subject',?)",user);
        for(String resource:new String[]{"project","task","journal"}) jdbc.update("insert into "+schema+"."+resource+"_create_idempotency(workspace_id,method,path,key,request_hash,response_status,response_body,created_at,expires_at) values(?,'POST',?,'existing','hash',201,'{}',now(),now()+interval '24 hours')",workspace,"/api/v1/"+resource+"s");
        var tables=java.util.List.of("users","auth_identities","workspaces","projects","tasks","journals","project_create_idempotency","task_create_idempotency","journal_create_idempotency");
        var before=new java.util.HashMap<String,java.util.List<java.util.Map<String,Object>>>();
        tables.forEach(table->before.put(table,jdbc.queryForList("select * from "+schema+"."+table)));
        var checksums=jdbc.queryForList("select version,checksum from "+schema+".flyway_schema_history where version is not null order by version");
        var upgraded=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load();
        upgraded.migrate(); upgraded.validate();
        tables.forEach(table->assertEquals(before.get(table),jdbc.queryForList("select * from "+schema+"."+table),table));
        assertEquals(42L,jdbc.queryForObject("select data_revision from "+schema+".workspaces",Long.class));
        assertEquals(checksums,jdbc.queryForList("select version,checksum from "+schema+".flyway_schema_history where version in ('1','2','3','4','5','6','7') order by version"));
        assertEquals(0L,jdbc.queryForObject("select count(*) from "+schema+".milestones",Long.class));
    }
}
