package com.kopite.devspace.link.application;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Component
@Validated
@ConfigurationProperties(prefix="app.link")
public class LinkLimits {
    public static final int HARD_MAX_LINKS=500;
    public static final int MAX_RESPONSE_BYTES=8*1024*1024;
    @Min(1) @Max(HARD_MAX_LINKS) private int maxLinks=HARD_MAX_LINKS;
    public int getMaxLinks() { return maxLinks; }
    public void setMaxLinks(int value) { maxLinks=value; }
}
