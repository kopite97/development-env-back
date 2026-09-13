package com.kopite.devspace.link.application.port;
import com.kopite.devspace.link.application.model.LinkSnapshot;
import com.kopite.devspace.link.application.query.LinkListFilter;
import java.util.*;
public interface LinkSearchRepository {
    List<LinkSnapshot> list(UUID workspace,LinkListFilter filter);
}
