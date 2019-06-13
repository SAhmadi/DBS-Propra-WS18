package de.hhu.cs.dbs.propra.configurations;

import de.hhu.cs.dbs.propra.models.Role;
import de.hhu.cs.dbs.propra.repositories.UserRepository;
import de.hhu.cs.dbs.propra.services.BasicHTTPAuthenticationService;
import de.hhu.cs.dbs.propra.services.CustomAuthorizationService;
import org.glassfish.jersey.server.model.AnnotatedMethod;

import javax.annotation.Priority;
import javax.annotation.security.DenyAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class SecurityFilter implements ContainerRequestFilter {
    @Context
    private ResourceInfo resourceInfo;
    @Inject
    private UserRepository userRepository;
    @Inject
    private BasicHTTPAuthenticationService authenticationService;
    @Inject
    private CustomAuthorizationService authorizationService;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        AuthorizationContext securityContext = (AuthorizationContext) authorizationService.getSecurityContext();
        requestContext.setSecurityContext(securityContext);
        final AnnotatedMethod annotatedMethod = new AnnotatedMethod(resourceInfo.getResourceMethod());

        if (annotatedMethod.isAnnotationPresent(DenyAll.class)) throw new ForbiddenException("Forbidden");
        if (!annotatedMethod.isAnnotationPresent(RolesAllowed.class)) return;
        RolesAllowed annotation = annotatedMethod.getAnnotation(RolesAllowed.class);
        List<Role> rolesAllowed = getRolesAllowedFromAnnotation(annotation);

        String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null) throw new NotAuthorizedException("Basic realm=\"Restricted access\"");
        if (!authenticationService.validateHeader(authorizationHeader)) throw new NotAuthorizedException("Basic realm=\"Restricted access\"");

        String base64EncodedCredentials = authorizationHeader.substring("Basic ".length());
        String name = authenticationService.getNameFromEncodedCredentials(base64EncodedCredentials);
        String password = authenticationService.getPasswordFromEncodedCredentials(base64EncodedCredentials);
        if (!authenticationService.authenticate(name, password)) throw new NotAuthorizedException("Basic realm=\"Restricted access\"");

        securityContext.setUser(userRepository.findByName(name).get());

        if (!authorizationService.authorise(name, rolesAllowed)) throw new ForbiddenException("Forbidden");
    }

    private List<Role> getRolesAllowedFromAnnotation(RolesAllowed annotation) {
        List<Role> rolesAllowed = new ArrayList<>();
        for (String value : annotation.value()) {
            rolesAllowed.add(Role.valueOf(value));
        }
        return rolesAllowed;
    }
}
