package com.kopite.devspace.widget.infrastructure;
import com.kopite.devspace.widget.application.*;
import org.springframework.context.annotation.*;
@Configuration
public class LocalWidgetConfiguration {
    @Bean WidgetTypeDefinition overviewWidget(){return new LocalWidgetDefinition("overview");}
    @Bean WidgetTypeDefinition boardWidget(){return new LocalWidgetDefinition("board");}
    @Bean WidgetTypeDefinition linksWidget(){return new LocalWidgetDefinition("links");}
    @Bean WidgetTypeDefinition journalWidget(){return new LocalWidgetDefinition("journal");}
    @Bean WidgetTypeDefinition milestoneWidget(){return new LocalWidgetDefinition("milestone");}
    @Bean WidgetTypeDefinition deployWidget(){return new LocalWidgetDefinition("deploy");}
}
