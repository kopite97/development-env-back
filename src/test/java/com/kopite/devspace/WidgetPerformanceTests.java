package com.kopite.devspace;

import com.kopite.devspace.widget.application.WidgetCommandService;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Descriptive local measurements, deliberately without performance pass/fail thresholds. */
class WidgetPerformanceTests extends WidgetTestSupport {
    @Autowired EntityManagerFactory emf; @Autowired DataSource source;
    @Autowired WidgetCommandService commands; @Autowired PersonalWorkspaceRepository workspaces;
    @Autowired PlatformTransactionManager manager;
    static final String CONFIG="{\"selection\":{\"kind\":\"all\"}}";
    Map<String,Object> latency(List<Double> values) {
        var sorted=values.stream().sorted().toList();
        return Map.of("samples",sorted.size(),"p50Ms",sorted.get((int)Math.ceil(sorted.size()*.5)-1),"p95Ms",sorted.get((int)Math.ceil(sorted.size()*.95)-1),"maxMs",sorted.getLast());
    }
    @Test void isolatedReadAndIndependentWriteMeasurements()throws Exception {
        var evidence=new LinkedHashMap<String,Object>();var stats=emf.unwrap(SessionFactory.class).getStatistics();stats.setStatisticsEnabled(true);
        try {
            var reads=new ArrayList<Object>();
            for(int count:List.of(6,60)) {
                var o=owner();var ids=new ArrayList<String>();for(int i=0;i<count;i++)ids.add(id(create(o,"board")));
                request(o,put(D),layout(0,ids.toArray(String[]::new)),200);
                for(int warm=0;warm<10;warm++)request(o,get(D),null,200);
                stats.clear();var times=new ArrayList<Double>();int bytes=0;
                for(int i=0;i<50;i++){long at=System.nanoTime();var r=request(o,get(D),null,200);times.add((System.nanoTime()-at)/1e6);bytes=r.getContentAsByteArray().length;}
                var row=new LinkedHashMap<String,Object>(latency(times));row.put("placements",count);row.put("preparedStatementsPerRequest",stats.getPrepareStatementCount()/50.0);row.put("responseBytes",bytes);reads.add(row);
            }
            evidence.put("layoutGetMockMvc",reads);
            var writes=new ArrayList<Object>();
            for(boolean same:List.of(true,false)) {
                var a=owner();var b=same?a:owner();UUID first=UUID.fromString(id(create(a,"board"))),second=UUID.fromString(id(create(b,"board")));
                var times=Collections.synchronizedList(new ArrayList<Double>());var transactionTimes=Collections.synchronizedList(new ArrayList<Double>());
                var poolSource=source.unwrap(HikariDataSource.class);var bean=poolSource.getHikariPoolMXBean();
                AtomicInteger maxActive=new AtomicInteger(),maxAwaiting=new AtomicInteger(),lockSamples=new AtomicInteger(),samples=new AtomicInteger();
                AtomicLong activeNanos=new AtomicLong(),waitingNanos=new AtomicLong();AtomicReference<Throwable> sampleFailure=new AtomicReference<>();
                // Monitoring uses one separate connection; it is excluded from Hikari occupancy.
                try(var monitor=DriverManager.getConnection(poolSource.getJdbcUrl(),poolSource.getUsername(),poolSource.getPassword());var sampler=Executors.newSingleThreadScheduledExecutor();var pool=Executors.newFixedThreadPool(2)) {
                    AtomicLong previous=new AtomicLong(System.nanoTime());
                    var sampling=sampler.scheduleWithFixedDelay(()->{try {
                        long now=System.nanoTime(),dt=now-previous.getAndSet(now);int active=bean.getActiveConnections(),waiting=bean.getThreadsAwaitingConnection();
                        maxActive.accumulateAndGet(active,Math::max);maxAwaiting.accumulateAndGet(waiting,Math::max);activeNanos.addAndGet(dt*active);waitingNanos.addAndGet(dt*waiting);samples.incrementAndGet();
                        try(var s=monitor.createStatement();var r=s.executeQuery("select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and wait_event_type='Lock' and query like '%workspaces%'")){r.next();lockSamples.addAndGet(r.getInt(1));}
                    }catch(Throwable ex){sampleFailure.set(ex);}},0,2,TimeUnit.MILLISECONDS);
                    long start=System.nanoTime();
                    for(long rev=1;rev<=30;rev++) {
                        final long expected=rev;var barrier=new CyclicBarrier(2);var futures=new ArrayList<Future<?>>();
                        for(int index=0;index<2;index++){final int k=index;futures.add(pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);long t=System.nanoTime();var tx=new TransactionTemplate(manager);
                            tx.executeWithoutResult(status->{long begin=System.nanoTime();commands.replace(k==0?a.user():b.user(),k==0?first:second,expected,"Measured",1,CONFIG);transactionTimes.add((System.nanoTime()-begin)/1e6);});
                            times.add((System.nanoTime()-t)/1e6);return null;}));}
                        for(var f:futures)f.get(20,TimeUnit.SECONDS);
                    }
                    double elapsed=(System.nanoTime()-start)/1e9;sampling.cancel(false);sampler.shutdown();assertTrue(sampler.awaitTermination(10,TimeUnit.SECONDS));assertNull(sampleFailure.get());
                    var row=new LinkedHashMap<String,Object>(latency(times));row.put("sameWorkspace",same);row.put("transactionsPerSecond",60/elapsed);row.put("transactionBodyLatency",latency(transactionTimes));row.put("maxActivePoolConnections",maxActive.get());row.put("maxPoolAwaiting",maxAwaiting.get());row.put("sampledActiveConnectionMs",activeNanos.get()/1e6);row.put("sampledPoolWaitMs",waitingNanos.get()/1e6);row.put("workspaceLockWaitSamples",lockSamples.get());row.put("monitorSamples",samples.get());writes.add(row);
                }
                assertEquals(31,jdbc.queryForObject("select revision from widgets where id=?",Integer.class,first));assertEquals(31,jdbc.queryForObject("select revision from widgets where id=?",Integer.class,second));
            }
            evidence.put("independentUpdateTransactions",writes);
            evidence.put("controlledWorkspaceLock",controlledLock());
            Path out=Path.of("build/reports/plan0013/performance.json");Files.createDirectories(out.getParent());Files.writeString(out,json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
        }finally{stats.setStatisticsEnabled(false);}
    }
    Map<String,Object> controlledLock()throws Exception {
        var o=owner();UUID id=UUID.fromString(id(create(o,"board")));var acquired=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var holder=pool.submit(()->new TransactionTemplate(manager).executeWithoutResult(s->{workspaces.lockByOwnerId(o.user()).orElseThrow();acquired.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}));
            assertTrue(acquired.await(10,TimeUnit.SECONDS));long begin=System.nanoTime();
            var waiter=pool.submit(()->{started.countDown();commands.replace(o.user(),id,1,"After lock",1,CONFIG);return (System.nanoTime()-begin)/1e6;});assertTrue(started.await(10,TimeUnit.SECONDS));
            boolean observed=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline){if(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and wait_event_type='Lock' and query like '%workspaces%'",Integer.class)>0){observed=true;break;}Thread.sleep(5);}
            assertTrue(observed,"Expected a real workspace database lock wait");Thread.sleep(100);double held=(System.nanoTime()-begin)/1e6;release.countDown();holder.get(10,TimeUnit.SECONDS);double elapsed=waiter.get(10,TimeUnit.SECONDS);
            return Map.of("workspaceLockObserved",observed,"blockedWindowMs",held,"waiterTransactionMs",elapsed,"independentUpdateSucceeded",true);
        }finally{release.countDown();}
    }
}
