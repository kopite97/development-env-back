package com.kopite.devspace.projectcategory.application;
public class CategoryQuotaException extends RuntimeException {
    public CategoryQuotaException() { super("A workspace may contain at most 100 Categories"); }
}
