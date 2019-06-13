package de.hhu.cs.dbs.propra.controllers;

import de.hhu.cs.dbs.propra.utilities.DatabaseUtility;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


@Path("/benutzer")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class UserController {
    @Inject
    private DataSource dataSource;

    @Context
    private SecurityContext securityContext;

    @Context
    private UriInfo uriInfo;


    @RolesAllowed({"EMPLOYEE", "ADMIN"})
    @GET
    public List<Map<String, Object>> getUsers(
            @QueryParam("email") String email,
            @QueryParam("alter") int alter
    ) throws NotFoundException {
        // Prepare query
        String query = "SELECT B.rowid, B.email, B.passwort, P.vorname, P.nachname, P.geburtsdatum\n" +
                "FROM benutzer B JOIN personen P ON B.personen_id=P.id\n" +
                "WHERE LOWER(B.email) LIKE LOWER('%?%') AND\n" +
                "STRFTIME('%Y', DATETIME('now')) - STRFTIME('%Y', P.geburtsdatum) >= ?;\n";

        query = query.replaceFirst("\\?", email != null ? email : "");
        query = query.replaceFirst("\\?", String.valueOf(alter > 0 ? alter : 0));

        List<Map<String, Object>> response = new ArrayList<>();
        Map<String, Object> entity;
        try (Connection connection = dataSource.getConnection()) {
            ResultSet resultSet = connection.createStatement().executeQuery(query);
            while (resultSet.next()) {
                entity = new HashMap<>();
                entity.put("benutzerid", resultSet.getInt("rowid"));
                entity.put("vorname", resultSet.getString("vorname"));
                entity.put("nachname", resultSet.getString("nachname"));
                entity.put("geburtsdatum", resultSet.getString("geburtsdatum"));
                entity.put("email", resultSet.getString("email"));
                entity.put("passwort", resultSet.getString("passwort"));
                response.add(entity);
            }

            resultSet.close();
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        if (response.isEmpty()) throw new NotFoundException("Not Found: Entities do not exits!");
        return response;
    }


    @POST
    public Response registerUser(
            @FormDataParam("vorname") String vorname,
            @FormDataParam("nachname") String nachname,
            @FormDataParam("geburtsdatum") String geburtsdatum,
            @FormDataParam("email") String email,
            @FormDataParam("passwort") String passwort
    ) throws BadRequestException {
        if (vorname == null) throw new BadRequestException("Bad Request: 'vorname' missing!");
        if (nachname == null) throw new BadRequestException("Bad Request: 'nachname' missing!");
        if (geburtsdatum == null) throw new BadRequestException("Bad Request: 'geburtsdatum' missing!");
        if (email == null) throw new BadRequestException("Bad Request: 'email' missing!");
        if (passwort == null) throw new BadRequestException("Bad Request: 'passwort' missing!");

        int rowId;
        try (Connection connection = dataSource.getConnection()) {
            // Find personenId of new user
            String queryGetPersonenId = "SELECT count(*) AS 'anzahl' FROM personen;\n";
            int personenId = connection.createStatement()
                    .executeQuery(queryGetPersonenId)
                    .getInt("anzahl") + 1;

            String query = "BEGIN TRANSACTION;\n";
            query += String.format("INSERT INTO personen VALUES ('%d', '%s', '%s', '%s');\n", personenId, vorname, nachname, geburtsdatum);
            query += String.format("INSERT INTO benutzer VALUES ('%s', '%s', '%d');\n", email, passwort, personenId);

            connection.createStatement().executeUpdate(query);

            // Get rowId of new user
            rowId = connection.createStatement()
                    .executeQuery("SELECT rowid FROM benutzer WHERE email='" + email + "';")
                    .getInt("rowid");

            connection.createStatement().executeUpdate("END TRANSACTION;\n");
        }
        catch (SQLException ex) { throw new BadRequestException(ex.getMessage()); }

        return Response.created(uriInfo.getAbsolutePathBuilder().path("/"+rowId).build()).build();
    }


    @Path("/{benutzerid}")
    @RolesAllowed({"USER","EMPLOYEE", "ADMIN"})
    @GET
    public Map<String, Object> getUser(
            @PathParam("benutzerid") int benutzerRowId
    ) throws SQLException, NotFoundException, ForbiddenException {
        if (benutzerRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        Connection connection = dataSource.getConnection();

        // Check user access
        Map<String, Object> restrictUserAccessResult = DatabaseUtility.restrictUserAccess(connection, securityContext, benutzerRowId);
        if ((boolean) restrictUserAccessResult.get("isUserAccessRestricted")) {
            connection.close();
            throw new ForbiddenException();
        }

        // Prepare query
        String query = String.format(
                "SELECT B.rowid AS 'rowid', P.vorname, P.nachname, P.geburtsdatum, B.email, B.passwort\n" +
                "FROM benutzer B, personen P\n" +
                "WHERE B.personen_id = P.id AND rowid = %d;\n",
                benutzerRowId
        );

        Map<String, Object> response = new HashMap<>();
        try (connection) {
            ResultSet resultSet = connection.createStatement().executeQuery(query);

            // Fill response
            while (resultSet.next()) {
                response.put("benutzerid", resultSet.getInt("rowid"));
                response.put("vorname", resultSet.getString("vorname"));
                response.put("nachname", resultSet.getString("nachname"));
                response.put("geburtsdatum", resultSet.getString("geburtsdatum"));
                response.put("email", resultSet.getString("email"));
                response.put("passwort", resultSet.getString("passwort"));
            }

            resultSet.close();
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        if (response.isEmpty()) throw new NotFoundException("Not Found: Entity does not exist!");
        return response;
    }


    @Path("/{benutzerid}")
    @RolesAllowed({"USER", "EMPLOYEE", "ADMIN"})
    @PATCH
    public Response updateUser(
            @PathParam("benutzerid") int benutzerRowId,
            @FormDataParam("vorname") String vorname,
            @FormDataParam("nachname") String nachname,
            @FormDataParam("geburtsdatum") String geburtsdatum,
            @FormDataParam("email") String email,
            @FormDataParam("passwort") String passwort
    ) throws SQLException, NotFoundException, ForbiddenException {
        if (benutzerRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        Connection  connection = dataSource.getConnection();

        // Check user access
        Map<String, Object> restrictUserAccessResult = DatabaseUtility.restrictUserAccess(connection, securityContext, benutzerRowId);
        if ((boolean) restrictUserAccessResult.get("isUserAccessRestricted")) {
            connection.close();
            throw new ForbiddenException("Forbidden: User can only update own data!");
        }

        // Prepare updates
        int userPersonenId;
        try {
            userPersonenId = connection
                    .createStatement()
                    .executeQuery("SELECT personen_id FROM benutzer WHERE rowid='" + benutzerRowId + "';")
                    .getInt("personen_id");
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exits!"); }

        String queryUpdatePersonen = "";
        if (vorname != null || nachname != null || geburtsdatum != null)  {
            queryUpdatePersonen = "UPDATE personen\nSET ";

            if (vorname != null)
                queryUpdatePersonen += String.format("vorname = '%s'", vorname);

            if (nachname != null) {
                if (vorname != null) queryUpdatePersonen += ", ";
                queryUpdatePersonen += String.format("nachname = '%s'", nachname);
            }

            if (geburtsdatum != null) {
                if (vorname != null || nachname != null) queryUpdatePersonen += ", ";
                queryUpdatePersonen += String.format("geburtsdatum = '%s'", geburtsdatum);
            }

            queryUpdatePersonen += String.format("\nWHERE id == '%d';\n", userPersonenId);
        }

        String queryUpdateBenutzer = "";
        if (email != null || passwort != null)  {
            queryUpdateBenutzer = "UPDATE benutzer\nSET ";

            if (email != null) {
                if ((int) restrictUserAccessResult.get("principalRowId") != benutzerRowId)
                    throw new ForbiddenException("Forbidden: Cannot change other users Email!");

                queryUpdateBenutzer += String.format("email = '%s'", email);
            }

            if (passwort != null) {
                if ((int) restrictUserAccessResult.get("principalRowId") != benutzerRowId)
                    throw new ForbiddenException("Forbidden: Cannot change other users Password!");

                if (email != null) queryUpdateBenutzer += ", ";
                queryUpdateBenutzer += String.format("passwort = '%s'", passwort);
            }
            queryUpdateBenutzer += String.format("\nWHERE rowid == '%d';\n", benutzerRowId);
        }

        try (connection) {
            String query = "BEGIN TRANSACTION;\n"  + queryUpdatePersonen + queryUpdateBenutzer + "END TRANSACTION;\n";
            connection.createStatement().executeUpdate(query);
        }
        catch (SQLException ex) { throw new BadRequestException(ex.getMessage()); }

        // Return result
        return Response.status(Response.Status.NO_CONTENT).build();
    }


    @Path("/{benutzerid}")
    @RolesAllowed({"USER", "EMPLOYEE", "ADMIN"})
    @DELETE
    public Response deleteUser(
            @PathParam("benutzerid") int benutzerRowId
    ) throws SQLException, NotFoundException, ForbiddenException {
        if (benutzerRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        Connection  connection = dataSource.getConnection();

        // Check user access
        Map<String, Object> restrictUserAccessResult = DatabaseUtility.restrictUserAccess(connection, securityContext, benutzerRowId);
        if ((boolean) restrictUserAccessResult.get("isUserAccessRestricted")) {
            connection.close();
            throw new ForbiddenException("Forbidden: User cannot delete other users!");
        }

        // Check employee and admin access
        int principalRowId = (int) restrictUserAccessResult.get("principalRowId");
        if ((securityContext.isUserInRole("EMPLOYEE") || securityContext.isUserInRole("ADMIN")) && principalRowId == benutzerRowId) {
            connection.close();
            throw new ForbiddenException("Forbidden: Employees cannot delete themselves!");
        }

        String query = String.format(
                "BEGIN TRANSACTION;\n" +
                "DELETE FROM benutzer WHERE rowid = '%d';\n" +
                "END TRANSACTION;\n",
                benutzerRowId
        );

        try (connection) {
            connection.createStatement().executeUpdate(query);
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        // Return result
        return Response.status(Response.Status.NO_CONTENT).build();
    }
}
