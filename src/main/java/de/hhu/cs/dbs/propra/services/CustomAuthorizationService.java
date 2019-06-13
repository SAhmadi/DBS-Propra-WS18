package de.hhu.cs.dbs.propra.services;

import de.hhu.cs.dbs.propra.configurations.AuthorizationContext;
import de.hhu.cs.dbs.propra.models.Role;
import de.hhu.cs.dbs.propra.repositories.UserRepository;

import javax.inject.Inject;
import javax.ws.rs.core.SecurityContext;
import java.util.List;

public class CustomAuthorizationService implements AuthorizationService {
    @Inject
    private AuthorizationContext securityContext;
    @Inject
    private UserRepository userRepository;

    @Override
    public boolean authorise(String name, List<Role> rolesAllowed) {
        return securityContext.getUserPrincipal().getName().equalsIgnoreCase(name) && rolesAllowed.stream().anyMatch(role -> securityContext.isUserInRole(role.name()));
    }

    public SecurityContext getSecurityContext() {
        return securityContext;
    }
}
