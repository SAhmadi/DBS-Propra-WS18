package de.hhu.cs.dbs.propra.controllers;


import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Path("/tickets")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class TicketController {
    @Inject
    private DataSource dataSource;

    @Context
    private SecurityContext securityContext;

    @Context
    private UriInfo uriInfo;


    @Path("/{ticketid}")
    @RolesAllowed({"USER", "EMPLOYEE", "ADMIN"})
    @GET
    public Map<String, Object> getTicket(
            @PathParam("ticketid") int ticketRowId
    ) throws SQLException, NotFoundException, BadRequestException {
        if (ticketRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        Connection connection = dataSource.getConnection();

        int vorstellungRowId;
        int vorstellungenFilmeId;
        String vorstellungenSaeleName;
        String vorstellungenDatumUhrzeit;
        String sitzreihe;
        int platznummer;
        boolean istKinderticket;
        int totalTicketPrice;
        try {
            ResultSet resultSet = connection
                    .createStatement()
                    .executeQuery("SELECT * FROM tickets T WHERE rowid = " + ticketRowId + ";");

            vorstellungenFilmeId = resultSet.getInt("vorstellungen_filme_id");
            vorstellungenSaeleName = resultSet.getString("vorstellungen_saele_name");
            vorstellungenDatumUhrzeit = resultSet.getString("vorstellungen_datum_uhrzeit");
            sitzreihe = resultSet.getString("sitzreihe");
            platznummer = resultSet.getInt("platznummer");
            istKinderticket = resultSet.getBoolean("ist_kinderticket");

            vorstellungRowId = connection.createStatement().executeQuery(String.format(
                    "SELECT rowid AS 'rowid' FROM vorstellungen WHERE filme_id = %d AND " +
                            "saele_name = '%s' AND datum_uhrzeit = '%s';",
                    vorstellungenFilmeId, vorstellungenSaeleName, vorstellungenDatumUhrzeit
            )).getInt("rowid");

            String querySelectTotalTicketPrice = String.format(
                    "SELECT SUM(wert) AS 'total_price' " +
                            "FROM betraege B " +
                            "JOIN tickets_besitzen_betraege TBB " +
                            "ON B.id = TBB.betraege_id " +
                            "WHERE TBB.tickets_vorstellungen_filme_id = %d AND TBB.tickets_vorstellungen_saele_name = '%s' AND " +
                            "TBB.tickets_vorstellungen_datum_uhrzeit = '%s' AND TBB.tickets_sitzreihe = '%s' AND TBB.tickets_platznummer = %d " +
                            "GROUP BY TBB.tickets_vorstellungen_filme_id, TBB.tickets_vorstellungen_saele_name, " +
                            "TBB.tickets_vorstellungen_datum_uhrzeit, TBB.tickets_sitzreihe, TBB.tickets_platznummer;\n",
                    vorstellungenFilmeId, vorstellungenSaeleName, vorstellungenDatumUhrzeit, sitzreihe, platznummer
            );
            totalTicketPrice = connection.createStatement()
                    .executeQuery(querySelectTotalTicketPrice)
                    .getInt("total_price");

            resultSet.close();
        }
        catch (SQLException ex) {
            connection.close();
            throw new NotFoundException("Not Found: Entity does not exist!");
        }

        int benutzerRowId;
        String benutzerEmail;
        Map<String, Object> response = new HashMap<>();
        try (connection) {
            benutzerEmail = connection.createStatement()
                    .executeQuery(
                            "SELECT benutzer_email " +
                            "FROM benutzer_reservieren_tickets " +
                            "WHERE tickets_vorstellungen_filme_id = " + vorstellungenFilmeId + " AND " +
                            "tickets_vorstellungen_saele_name = '" + vorstellungenSaeleName + "' AND " +
                            "tickets_vorstellungen_datum_uhrzeit = '" + vorstellungenDatumUhrzeit + "' AND " +
                            "tickets_sitzreihe = '" + sitzreihe + "' AND " +
                            "tickets_platznummer = " + platznummer + ";"
                    ).getString("benutzer_email");

            benutzerRowId = connection.createStatement()
                    .executeQuery("SELECT rowid AS 'rowid' FROM benutzer WHERE email = '" + benutzerEmail + "';")
                    .getInt("rowid");
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exits!"); }

        // Check user access
        if (
                securityContext.isUserInRole("USER") && !securityContext.isUserInRole("EMPLOYEE") &&
                !securityContext.getUserPrincipal().getName().equals(benutzerEmail)
        ) {
            connection.close();
            throw new ForbiddenException("Forbidden: User can only request own tickets!");
        }
        else {
            response.put("ticketid", ticketRowId);
            response.put("kind", istKinderticket);
            response.put("reihe", sitzreihe);
            response.put("nummer", platznummer);
            response.put("gesamtpreis", totalTicketPrice);
            response.put("vorstellungid", vorstellungRowId);
            response.put("benutzerid", benutzerRowId);
        }

        if (response.isEmpty())
            throw new NotFoundException("Not Found: Entity does not exist!");

        return response;
    }


    @Path("/{ticketid}")
    @RolesAllowed({"USER", "EMPLOYEE", "ADMIN"})
    @DELETE
    public Response deleteTicket(
            @PathParam("ticketid") int ticketRowId
    ) throws NotFoundException, ForbiddenException {
        if (ticketRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        String ticketBenutzerEmail;
        try {
            ticketBenutzerEmail = dataSource.getConnection()
                    .createStatement()
                    .executeQuery("SELECT benutzer_email FROM benutzer_reservieren_tickets WHERE rowid = " + ticketRowId + ";\n")
                    .getString("benutzer_email");
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exits!"); }
        if (
                securityContext.isUserInRole("USER") && !securityContext.isUserInRole("EMPLOYEE") &&
                !securityContext.getUserPrincipal().getName().equals(ticketBenutzerEmail)
        ) { throw new ForbiddenException("Forbidden: User can only delete own ticket!"); }

        try (Connection connection = dataSource.getConnection()) {
            String query = String.format(
                    "DELETE FROM benutzer_reservieren_tickets WHERE rowid = %d;\n",
                    ticketRowId
            );
            connection.createStatement().executeUpdate(query);
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        // Return result
        return Response.status(Response.Status.NO_CONTENT).build();
    }
}
