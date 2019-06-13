package de.hhu.cs.dbs.propra.configurations;

import de.hhu.cs.dbs.propra.models.Role;
import de.hhu.cs.dbs.propra.models.User;

import javax.ws.rs.core.SecurityContext;
import java.security.Principal;

public class AuthorizationContext implements SecurityContext {
    private User user;

    @Override
    public Principal getUserPrincipal() {
        return user;
    }

    @Override
    public boolean isUserInRole(String role) {
        return user != null && user.getRoles().contains(Role.valueOf(role));
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public String getAuthenticationScheme() {
        return BASIC_AUTH;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
