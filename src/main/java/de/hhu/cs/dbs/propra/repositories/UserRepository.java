package de.hhu.cs.dbs.propra.repositories;

import de.hhu.cs.dbs.propra.models.User;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findByName(String name);

    long countByNameAndPassword(String name, String password);
}
