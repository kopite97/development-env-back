package com.kopite.devspace.link.presentation.dto;
import com.kopite.devspace.link.application.query.LinkListFilter;
public record LinkListRequest(String scope,String query) {public LinkListFilter filter(){return new LinkListFilter(scope,query);}}
