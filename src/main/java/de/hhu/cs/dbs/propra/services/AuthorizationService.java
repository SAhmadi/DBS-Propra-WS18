package de.hhu.cs.dbs.propra.services;

import de.hhu.cs.dbs.propra.models.Role;

import javax.ws.rs.core.SecurityContext;
import java.util.List;

public interface AuthorizationService {
    boolean authorise(String name, List<Role> rolesAllowed);

    SecurityContext getSecurityContext();
}
