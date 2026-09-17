package com.kopite.devspace;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.*;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Copy to the preserved pre-Widget source test package; never register in final runtime. */
class LegacyLayoutMeasurementTests extends WidgetTestSupport {
    @Autowired EntityManagerFactory emf;
    @Test void baseline()throws Exception {
        var rows=new ArrayList<Object>();var stats=emf.unwrap(SessionFactory.class).getStatistics();stats.setStatisticsEnabled(true);
        try {for(int count:List.of(6,60)) {
            var o=owner();var widgets=new ArrayList<Object>();
            for(int i=0;i<count;i++)widgets.add(Map.of("id","widget-"+i,"type","board","title","Example","selection",Map.of("kind","all"),"size","wide"));
            String path="/api/v2/dashboards/home";request(o,put(path),json.writeValueAsString(Map.of("schemaVersion",2,"revision",0,"widgets",widgets)),200);
            for(int i=0;i<10;i++)request(o,get(path),null,200);
            stats.clear();var values=new ArrayList<Double>();int bytes=0;
            for(int i=0;i<50;i++){long start=System.nanoTime();var r=request(o,get(path),null,200);values.add((System.nanoTime()-start)/1e6);bytes=r.getContentAsByteArray().length;}
            values.sort(Double::compare);rows.add(Map.of("widgets",count,"samples",50,"p50Ms",values.get(24),"p95Ms",values.get(47),"maxMs",values.getLast(),"preparedStatementsPerRequest",stats.getPrepareStatementCount()/50.0,"responseBytes",bytes));
        }}finally{stats.setStatisticsEnabled(false);}
        Path output=Path.of("build/reports/plan0013/legacy-performance.json");Files.createDirectories(output.getParent());Files.writeString(output,json.writerWithDefaultPrettyPrinter().writeValueAsString(rows));
    }
}
