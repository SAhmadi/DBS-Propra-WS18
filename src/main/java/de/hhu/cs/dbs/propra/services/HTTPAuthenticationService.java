package de.hhu.cs.dbs.propra.services;

import de.hhu.cs.dbs.propra.repositories.UserRepository;

import javax.inject.Inject;

public abstract class HTTPAuthenticationService implements AuthenticationService {
    @Inject
    protected UserRepository userRepository;

    public abstract boolean isSecure();

    public abstract boolean validateHeader(String header);
}
