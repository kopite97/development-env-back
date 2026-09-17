package com.kopite.devspace.widget.domain;

public class WidgetException extends RuntimeException {
    private final String code;
    private final String field;
    public WidgetException(String code,String field) { super(code);this.code=code;this.field=field; }
    public String code(){return code;}
    public String field(){return field;}
    public static WidgetException invalid(String field){return new WidgetException("VALIDATION_ERROR",field);}
    public static WidgetException missing(){return new WidgetException("RESOURCE_NOT_FOUND",null);}
    public static WidgetException conflict(){return new WidgetException("REVISION_CONFLICT",null);}
}
