package de.hhu.cs.dbs.propra.controllers;

import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Path("/bewertungen")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RatingController {
    @Inject
    private DataSource dataSource;

    @Context
    private SecurityContext securityContext;

    @Context
    private UriInfo uriInfo;


    @Path("/{bewertungid}")
    @RolesAllowed({"USER", "EMPLOYEE", "ADMIN"})
    @PATCH
    public Response updateRating(
            @PathParam("bewertungid") int bewertungRowId,
            @FormDataParam("sterne") int sterne
    ) throws SQLException, NotFoundException, BadRequestException {
        if (bewertungRowId < 1) throw new NotFoundException("Not Found: Entity does not exist!");
        if (sterne < 1) throw new BadRequestException("Bad Request: 'sterne' missing!");
        if (sterne > 10) throw new BadRequestException("Bad Request: 'sterne' invalid!");

        Connection connection = dataSource.getConnection();

        // Prepare updates
        String reqBewertungEmail;
        int reqFilmeId;
        try {
            ResultSet resultSet = connection
                    .createStatement()
                    .executeQuery("SELECT benutzer_email, filme_id FROM benutzer_bewerten_filme WHERE rowid = " + bewertungRowId + ";");

            if (resultSet.next()) {
                reqBewertungEmail = resultSet.getString("benutzer_email");
                reqFilmeId = resultSet.getInt("filme_id");
            }
            else throw new NotFoundException();

            resultSet.close();
        }
        catch (NotFoundException ex) {
            connection.close();
            throw new NotFoundException("Not Found: Entity has no ratings!");
        }
        catch (SQLException ex) {
            connection.close();
            throw new NotFoundException("Not Found: Entity does not exist!");
        }

        if (!reqBewertungEmail.equals(securityContext.getUserPrincipal().getName())) {
            connection.close();
            throw new ForbiddenException("Forbidden: User can only update own ratings!");
        }

        try (connection) {
            String queryUpdateCover = "UPDATE benutzer_bewerten_filme SET sterne = ? WHERE benutzer_email = ? AND filme_id = ?;";
            PreparedStatement preparedStatement = connection.prepareStatement(queryUpdateCover);
            preparedStatement.closeOnCompletion();
            preparedStatement.setInt(1, sterne);
            preparedStatement.setString(2, reqBewertungEmail);
            preparedStatement.setInt(3, reqFilmeId);
            preparedStatement.executeUpdate();
        }
        catch (SQLException ex) { throw new BadRequestException(ex.getMessage()); }

        // Return result
        return Response.status(Response.Status.NO_CONTENT).build();
    }


    @Path("/{bewertungid}")
    @RolesAllowed({"USER", "EMPLOYEE", "ADMIN"})
    @DELETE
    public Response deleteActorForMovie(
            @PathParam("bewertungid") int bewertungRowId
    ) throws NotFoundException {
        if (bewertungRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        String reqBewertungEmail;
        int reqFilmeId;
        ResultSet resultSet;
        try {
            resultSet = dataSource.getConnection().createStatement()
                    .executeQuery("SELECT benutzer_email, filme_id FROM benutzer_bewerten_filme WHERE rowid = " + bewertungRowId + ";");

            if (resultSet.next()) {
                reqBewertungEmail = resultSet.getString("benutzer_email");
                reqFilmeId = resultSet.getInt("filme_id");
            }
            else throw new NotFoundException();
        }
        catch (NotFoundException ex) { throw new NotFoundException("Not Found: Entity has no ratings!"); }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exist!"); }

        if (
                securityContext.isUserInRole("USER") && !securityContext.isUserInRole("EMPLOYEE") &&
                !reqBewertungEmail.equals(securityContext.getUserPrincipal().getName())
        ) {
            throw new ForbiddenException("Forbidden: User can only delete own ratings!");
        }

        try (Connection connection = dataSource.getConnection()) {
            String query = String.format(
                    "DELETE FROM benutzer_bewerten_filme WHERE benutzer_email = '%s' AND filme_id = %d;\n",
                    reqBewertungEmail, reqFilmeId
            );
            connection.createStatement().executeUpdate(query);
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        // Return result
        return Response.status(Response.Status.NO_CONTENT).build();
    }
}
