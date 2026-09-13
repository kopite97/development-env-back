package com.kopite.devspace.link.application.exception;
public class LinkQuotaException extends RuntimeException {
    public LinkQuotaException(int max) { super("Workspace Link limit is " + max + "; delete a Link before creating another"); }
}
