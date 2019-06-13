package de.hhu.cs.dbs.propra.models;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

public class User implements Principal {
    private final String name;
    private final List<Role> roles;

    public User(String name) {
        this(name, Collections.emptyList());
    }

    public User(String name, List<Role> roles) {
        this.name = name;
        this.roles = roles;
    }

    public String getName() {
        return name;
    }

    public List<Role> getRoles() {
        return Collections.unmodifiableList(roles);
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                ", roles=" + roles +
                '}';
    }
}
