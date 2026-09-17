package com.kopite.devspace.widget.application;
import com.kopite.devspace.widget.domain.WidgetSelection;
import com.fasterxml.jackson.annotation.JsonInclude;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocalWidgetConfig(WidgetSelection selection,Integer limit) implements WidgetConfig {}
