package de.hhu.cs.dbs.propra.utilities;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.SecurityContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class DatabaseUtility {
    public static Map<String, Object> restrictUserAccess(
        Connection connection,
        SecurityContext securityContext,
        int pathParamRowId
    ) throws SQLException {
        int principalRowId;

        try {
            principalRowId = connection
                .createStatement()
                .executeQuery(
                        "SELECT rowid AS 'rowid' FROM benutzer WHERE email = '" +
                        securityContext.getUserPrincipal().getName() + "'"
                )
                .getInt("rowid");
        }
        catch (SQLException ex) {
            connection.close();
            throw new BadRequestException();
        }

        return Map.of(
                "principalRowId", principalRowId,
                "isUserAccessRestricted", securityContext.isUserInRole("USER") &&
                    !securityContext.isUserInRole("EMPLOYEE") &&
                    pathParamRowId != principalRowId
        );
    }
}
