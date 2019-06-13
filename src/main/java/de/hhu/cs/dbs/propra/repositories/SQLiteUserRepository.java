package de.hhu.cs.dbs.propra.repositories;

import de.hhu.cs.dbs.propra.models.Role;
import de.hhu.cs.dbs.propra.models.User;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteUserRepository implements UserRepository {
    @Inject
    private DataSource dataSource;

    @Override
    public Optional<User> findByName(String name) {
        Optional<User> user = Optional.empty();
        try (Connection connection = dataSource.getConnection()) {
            String query =  "SELECT  email, 'USER'\n" +
                    "FROM    benutzer\n" +
                    "WHERE   email = '" + name + "'\n" +
                    "UNION\n" +
                    "SELECT  benutzer_email, 'EMPLOYEE'\n" +
                    "FROM    mitarbeiter\n" +
                    "WHERE   benutzer_email = '" + name + "'\n" +
                    "UNION\n" +
                    "SELECT  mitarbeiter_benutzer_email, 'ADMIN'\n" +
                    "FROM    administratoren\n" +
                    "WHERE   mitarbeiter_benutzer_email = '" + name + "';";

            ResultSet resultSet = connection.createStatement().executeQuery(query);
            user = Optional.ofNullable(extractUser(resultSet));
        }
        catch (SQLException ex) {
            System.err.println(ex.getMessage());
        }
        return user;
    }


    public long countByNameAndPassword(String name, String password) {
        try (Connection connection = dataSource.getConnection()) {
            String query =  "SELECT count(*)\n" +
                    "FROM (\n" +
                    "SELECT email, passwort\n" +
                    "FROM   benutzer\n" +
                    "WHERE  email = '" + name + "' AND\n" +
                    "passwort = '" + password + "'\n" +
                    ");";

            ResultSet resultSet = connection.createStatement().executeQuery(query);
            resultSet.next();
            return resultSet.getLong(1);
        }
        catch (SQLException ex) {
            System.err.println(ex.getMessage());
        }
        return 0;
    }


    private User extractUser(ResultSet resultSet) {
        try {
            if (!resultSet.next()) return null;

            String name = resultSet.getString(1);
            List<Role> roles = new ArrayList<>();
            do {
                roles.add(Role.valueOf(resultSet.getString(2)));
            } while (resultSet.next());

            return new User(name, roles);
        } catch (SQLException ex) {
            return null;
        }
    }
}
