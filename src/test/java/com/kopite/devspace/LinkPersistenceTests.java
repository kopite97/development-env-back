package com.kopite.devspace;
import com.kopite.devspace.link.domain.Link;
import com.kopite.devspace.link.domain.LinkCollectionRepository;
import com.kopite.devspace.link.domain.LinkConflictException;
import com.kopite.devspace.link.domain.LinkRepository;
import com.kopite.devspace.link.domain.LinkValidationException;
import com.kopite.devspace.link.domain.LinkValues;

import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;
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
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LinkPersistenceTests {
    private final UserWorkspaceCreationService users;
    private final LinkRepository links;
    private final LinkCollectionRepository collections;
    private final PersonalWorkspaceRepository workspaces;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final DataSource dataSource;
    @Autowired
    LinkPersistenceTests(UserWorkspaceCreationService users,LinkRepository links,LinkCollectionRepository collections,
            PersonalWorkspaceRepository workspaces,JdbcTemplate jdbc,PlatformTransactionManager manager,DataSource dataSource) {
        this.users=users;this.links=links;this.collections=collections;this.workspaces=workspaces;
        this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.dataSource=dataSource;
    }
    private UUID workspace() { return users.createOrReuse("link-persistence",UUID.randomUUID().toString(),"Owner").workspace().getId(); }
    private LinkValues values() { return new LinkValues(" Label "," preserved "," https://example.com/a?q=1#f ", null); }
    @Test
    void lazyCollectionSwapAndDeletePreserveAtomicState() {
        UUID w=workspace();
        tx.executeWithoutResult(s->assertTrue(collections.find(w).isEmpty()));
        assertEquals(0L,jdbc.queryForObject("select count(*) from link_collections where workspace_id=?",Long.class,w));
        var ids=tx.execute(s->{
            workspaces.lockByOwnerId(jdbc.queryForObject("select owner_user_id from workspaces where id=?",UUID.class,w)).orElseThrow();
            var c=collections.lockOrCreate(w);assertEquals(0,c.getRevision());
            var a=links.save(Link.create(w,values(),0,Instant.now()));
            var b=links.save(Link.create(w,values(),1,Instant.now()));c.advance();
            return java.util.List.of(a.getId(),b.getId());
        });assertNotNull(ids);
        tx.executeWithoutResult(s->{
            var a=links.lockOwned(w,ids.get(0)).orElseThrow();var b=links.lockOwned(w,ids.get(1)).orElseThrow();
            assertEquals("Label",a.getLabel());assertEquals(" preserved ",a.getDescription());
            assertEquals(a.getCreatedAt(),a.getUpdatedAt());assertTrue(links.findOwned(UUID.randomUUID(),a.getId()).isEmpty());
            var created=a.getCreatedAt();links.beginReorder();a.move(1,created.plusSeconds(1));b.move(0,created.plusSeconds(1));links.finishReorder();
            assertEquals(2,a.getRevision());assertEquals(created,a.getCreatedAt());
            a.move(1,created.plusSeconds(2));assertEquals(2,a.getRevision());
        });
        assertThrows(RuntimeException.class,()->tx.executeWithoutResult(s->{
            var all=links.lockAll(w);links.beginReorder();all.forEach(l->l.move(0,Instant.now()));links.finishReorder();
        }));
        assertEquals(java.util.List.of(0L,1L),jdbc.queryForList("select position from links where workspace_id=? order by position",Long.class,w));
        tx.executeWithoutResult(s->{links.lockAll(w).forEach(links::delete);collections.lockOrCreate(w).advance();});
        assertEquals(0L,jdbc.queryForObject("select count(*) from links where workspace_id=?",Long.class,w));
        assertEquals(2L,jdbc.queryForObject("select revision from link_collections where workspace_id=?",Long.class,w));
        assertEquals(0L,jdbc.queryForObject("select data_revision from workspaces where id=?",Long.class,w));
    }
    @Test
    void domainValidationAndOverflow() {
        String emoji="\uD83D\uDE00";
        assertEquals(100,new LinkValues(emoji.repeat(50),"","https://example.com", null).label().length());
        for(String label:new String[]{null,"","\u2003",emoji.repeat(51)})
            assertThrows(LinkValidationException.class,()->new LinkValues(label,"","https://example.com", null));
        for(String url:new String[]{"","/a","//example.com","javascript:alert(1)","ftp://example.com","https://u:p@example.com","https://@example.com","https://example.com:bad","https://","https://a b"})
            assertThrows(LinkValidationException.class,()->new LinkValues("a","",url, null),url);
        for(String value:new String[]{"","bad","ALL"}) assertThrows(LinkValidationException.class,()->new com.kopite.devspace.link.application.command.LinkProjectSelection(true,value));
        assertNull(new com.kopite.devspace.link.application.command.LinkProjectSelection(true,null).id());
        assertThrows(LinkValidationException.class,()->new LinkValues("a","x".repeat(301),"https://example.com", null));
        assertThrows(LinkValidationException.class,()->new LinkValues("a","","https://example.com/"+"x".repeat(2000), null));
        var l=Link.create(UUID.randomUUID(),values(),0,Instant.now());
        l.update(1,values(),Instant.now());assertEquals(2,l.getRevision());
        assertThrows(LinkConflictException.class,()->l.checkRevision(1));
        assertThrows(LinkConflictException.class,()->Link.nextPosition(Link.MAX_REVISION));
        assertEquals(0,Link.nextPosition(-1));
    }
    @Test
    void databaseChecksAndMaxRevisionDeletion() {
        UUID w=workspace();var id=tx.execute(s->{collections.lockOrCreate(w);return links.save(Link.create(w,values(),0,Instant.now())).getId();});
        for(String assignment:new String[]{"label=null","label=' '","label=repeat('x',101)","description=null","description=repeat('x',301)","url=' '","url=repeat('x',2001)","project_id='00000000-0000-0000-0000-000000000001'","position=-1","position=9007199254740992","revision=0","revision=9007199254740992"})
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update links set "+assignment+" where id=?",id),assignment);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update links set workspace_id=? where id=?",UUID.randomUUID(),id));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update link_collections set revision=-1 where workspace_id=?",w));
        jdbc.update("update links set revision=? where id=?",Link.MAX_REVISION,id);
        tx.executeWithoutResult(s->{var l=links.lockOwned(w,id).orElseThrow();assertThrows(LinkConflictException.class,l::checkCanAdvance);l.checkRevision(Link.MAX_REVISION);links.delete(l);});
    }
    @Test
    void upgradesPopulatedV9WithoutRewritingDataOrChecksums() {
        String schema="link_upgrade_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("9").load().migrate();
        UUID user=UUID.randomUUID(); UUID workspace=UUID.randomUUID(); UUID project=UUID.randomUUID();
        jdbc.update("insert into "+schema+".users values(?,'Existing',now(),now(),null)",user);
        jdbc.update("insert into "+schema+".workspaces values(?,?,'Existing',7,42,now(),now())",workspace,user);
        jdbc.update("insert into "+schema+".projects(id,workspace_id,name,scope,stack,created_at,updated_at) values(?,?,'Existing','server','Java',now(),now())",project,workspace);
        jdbc.update("insert into "+schema+".tasks(id,workspace_id,project_id,title,created_at,updated_at) values(?,?,?,'Existing',now(),now())",UUID.randomUUID(),workspace,project);
        jdbc.update("insert into "+schema+".journals(id,workspace_id,project_id,title,body,entry_date,created_at,updated_at) values(?,?,?,'Existing','Body','2024-02-29',now(),now())",UUID.randomUUID(),workspace,project);
        jdbc.update("insert into "+schema+".milestones(id,workspace_id,project_id,title,created_at,updated_at) values(?,?,?,'Existing',now(),now())",UUID.randomUUID(),workspace,project);
        jdbc.update("insert into "+schema+".auth_identities(issuer,subject,user_id) values('upgrade','subject',?)",user);
        for(String resource:new String[]{"project","task","journal","milestone"}) jdbc.update("insert into "+schema+"."+resource+"_create_idempotency(workspace_id,method,path,key,request_hash,response_status,response_body,created_at,expires_at) values(?,'POST',?,'existing','hash',201,'{}',now(),now()+interval '24 hours')",workspace,"/api/v1/"+resource+"s");
        var tables=java.util.List.of("users","auth_identities","workspaces","projects","tasks","journals","project_create_idempotency","task_create_idempotency","journal_create_idempotency","milestones","milestone_create_idempotency");
        var before=new java.util.HashMap<String,java.util.List<java.util.Map<String,Object>>>();
        tables.forEach(table->before.put(table,jdbc.queryForList("select * from "+schema+"."+table)));
        var checksums=jdbc.queryForList("select version,checksum from "+schema+".flyway_schema_history where version is not null order by version");
        var upgraded=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("14").load();
        upgraded.migrate(); upgraded.validate();
        tables.forEach(table->{
            var after=jdbc.queryForList("select * from "+schema+"."+table);
            if(table.equals("projects"))after.forEach(row->{assertTrue(row.containsKey("category_id"));assertNull(row.remove("category_id"));});
            assertEquals(before.get(table),after,table);
        });
        assertEquals(42L,jdbc.queryForObject("select data_revision from "+schema+".workspaces",Long.class));
        assertEquals(checksums,jdbc.queryForList("select version,checksum from "+schema+".flyway_schema_history where version in ('1','2','3','4','5','6','7','8','9') order by version"));
        assertEquals(0L,jdbc.queryForObject("select count(*) from "+schema+".links",Long.class));
    }
}
