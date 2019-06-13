package de.hhu.cs.dbs.propra.controllers;

import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/vorstellungen")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ShowingController {
    @Inject
    private DataSource dataSource;

    @Context
    private SecurityContext securityContext;

    @Context
    private UriInfo uriInfo;


    @Path("/{vorstellungid}")
    @GET
    public Map<String, Object> getShowing(
            @PathParam("vorstellungid") int vorstellungRowId
    ) throws NotFoundException {
        if (vorstellungRowId < 1) throw new NotFoundException("Not Found: Entity does not exist");

        // Prepare query
        Map<String, Object> response = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            String query =
                    "SELECT V.rowid AS 'rowid', V.ist_drei_d, V.sprache, V.datum_uhrzeit, F.rowid AS 'filme_rowid', S.rowid AS 'saele_rowid' " +
                    "FROM vorstellungen V, filme F, saele S " +
                    "WHERE V.rowid = " + vorstellungRowId + " AND " +
                    "V.filme_id = F.id AND V.saele_name = S.name;\n";
            ResultSet resultSet  = connection.createStatement().executeQuery(query);

            // Fill response
            while (resultSet.next()) {
                response.put("vorstellungid", vorstellungRowId);
                response.put("dreid", resultSet.getBoolean("ist_drei_d"));
                response.put("sprache", resultSet.getString("sprache"));
                response.put("zeitstempel", resultSet.getString("datum_uhrzeit"));
                response.put("filmid", resultSet.getInt("filme_rowid"));
                response.put("saalid", resultSet.getInt("saele_rowid"));
            }

            resultSet.close();
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        if (response.isEmpty()) throw new NotFoundException("Not Found: Entity does not exist!");
        return response;
    }


    @Path("/{vorstellungid}")
    @RolesAllowed({"EMPLOYEE", "ADMIN"})
    @PATCH
    public Response updateShowing(
            @PathParam("vorstellungid") int vorstellungRowId,
            @FormDataParam("dreid") @DefaultValue("false") boolean dreiD,
            @FormDataParam("sprache") String sprache,
            @FormDataParam("zeitstempel") String zeitstempel,
            @FormDataParam("saalid") int saalRowId
    ) throws NotFoundException, BadRequestException {
        if (vorstellungRowId < 1) throw new NotFoundException("Not Found: Entity does not exist!");

        try {
            ResultSet resultSetCheckVorstellungExists = dataSource.getConnection()
                    .createStatement()
                    .executeQuery("SELECT rowid AS 'rowid' FROM vorstellungen WHERE rowid = " + vorstellungRowId);
            if (!resultSetCheckVorstellungExists.next()) throw new NotFoundException();
            else resultSetCheckVorstellungExists.close();
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exits!"); }

        String saalName = "";
        if (saalRowId > 0) {
            try {
                saalName = dataSource.getConnection().createStatement()
                        .executeQuery("SELECT name FROM saele WHERE rowid = " + saalRowId + ";\n")
                        .getString("name");
            }
            catch (SQLException ex) { throw new BadRequestException("Bad Request: 'saalid' is invalid!"); }
        }

        try (Connection connection = dataSource.getConnection()) {
            String query = "UPDATE vorstellungen SET ist_drei_d = " + ((dreiD) ? 1 : 0) + ", " +
                    ((sprache != null) ? "sprache = '" + sprache + "', " : "") +
                    ((zeitstempel != null) ? "datum_uhrzeit = STRFTIME('%Y-%m-%dT%H:%M:%fZ', '" + zeitstempel + "'), " : "") +
                    ((!saalName.equals("")) ? "saele_name = '" + saalName + "', " : "") +
                    " WHERE rowid = " + vorstellungRowId;
            String queryFinal = query.substring(0, query.lastIndexOf(","));
            queryFinal += query.substring(query.lastIndexOf(", ") + 1);
            queryFinal += ";";

            connection.createStatement().executeUpdate(queryFinal);
        } catch (SQLException ex) { throw new BadRequestException(ex.getMessage()); }

        // Return result
        return Response.status(Response.Status.NO_CONTENT).build();
    }


    @Path("/{vorstellungid}")
    @RolesAllowed({"EMPLOYEE", "ADMIN"})
    @DELETE
    public Response deleteShowing(
            @PathParam("vorstellungid") int vorstellungRowId
    ) throws NotFoundException {
        if (vorstellungRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        try (Connection connection = dataSource.getConnection()) {
            String query = "DELETE FROM vorstellungen WHERE rowid = " + vorstellungRowId + ";";
            connection.createStatement().executeUpdate(query);
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        // Return result
        return Response.status(Response.Status.NO_CONTENT).build();
    }


    @Path("/{vorstellungid}/tickets")
    @RolesAllowed({"EMPLOYEE", "ADMIN"})
    @GET
    public List<Map<String, Object>> getTicketsForShowing(
            @PathParam("vorstellungid") int vorstellungRowId
    ) throws NotFoundException {
        if (vorstellungRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        // Prepare query
        List<Map<String, Object>> response = new ArrayList<>();
        Map<String, Object> ticket;
        try (Connection connection = dataSource.getConnection()) {
            String query = String.format(
                    "SELECT DISTINCT T.rowid AS 'tickets_rowid',\n" +
                    "       T.ist_kinderticket,\n" +
                    "       T.sitzreihe,\n" +
                    "       T.platznummer,\n" +
                    "       V.rowid AS 'vorstellungen_rowid',\n" +
                    "       BEN.rowid AS 'benutzer_rowid',\n" +
                    "       SUM(BET.wert) AS 'total_price'\n" +
                    "FROM vorstellungen V,\n" +
                    "     tickets T,\n" +
                    "     benutzer_reservieren_tickets BRT,\n" +
                    "     benutzer BEN,\n" +
                    "     betraege BET,\n" +
                    "     tickets_besitzen_betraege TBB\n" +
                    "WHERE V.rowid = %d AND\n" +
                    "      T.vorstellungen_filme_id = V.filme_id AND\n" +
                    "      T.vorstellungen_saele_name = V.saele_name AND\n" +
                    "      T.vorstellungen_datum_uhrzeit = V.datum_uhrzeit AND\n" +
                    "      BRT.tickets_vorstellungen_filme_id = T.vorstellungen_filme_id AND\n" +
                    "      BRT.tickets_vorstellungen_saele_name = T.vorstellungen_saele_name AND\n" +
                    "      BRT.tickets_vorstellungen_datum_uhrzeit = T.vorstellungen_datum_uhrzeit AND\n" +
                    "      BRT.tickets_sitzreihe = T.sitzreihe AND\n" +
                    "      BRT.tickets_platznummer = T.platznummer AND\n" +
                    "      BRT.benutzer_email = BEN.email AND\n" +
                    "      BET.id = TBB.betraege_id AND\n" +
                    "      TBB.tickets_vorstellungen_filme_id = T.vorstellungen_filme_id AND\n" +
                    "      TBB.tickets_vorstellungen_saele_name = T.vorstellungen_saele_name AND\n" +
                    "      TBB.tickets_vorstellungen_datum_uhrzeit = T.vorstellungen_datum_uhrzeit AND\n" +
                    "      TBB.tickets_sitzreihe = T.sitzreihe AND\n" +
                    "      TBB.tickets_platznummer = T.platznummer\n" +
                    "GROUP BY T.vorstellungen_filme_id,\n" +
                    "         T.vorstellungen_saele_name,\n" +
                    "         T.vorstellungen_datum_uhrzeit,\n" +
                    "         T.sitzreihe,\n" +
                    "         T.platznummer;",
                    vorstellungRowId
            );

            ResultSet resultSet = connection.createStatement().executeQuery(query);
            while (resultSet.next()) {
                ticket = new HashMap<>();
                ticket.put("ticketid", resultSet.getInt("tickets_rowid"));
                ticket.put("kind", resultSet.getBoolean("ist_kinderticket"));
                ticket.put("reihe", resultSet.getString("sitzreihe"));
                ticket.put("nummer", resultSet.getInt("platznummer"));
                ticket.put("gesamtpreis", resultSet.getInt("total_price"));
                ticket.put("vorstellungid", resultSet.getInt("vorstellungen_rowid"));
                ticket.put("benutzerid", resultSet.getInt("benutzer_rowid"));
                response.add(ticket);
            }

            resultSet.close();
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        if (response.isEmpty()) throw new NotFoundException("Not Found: Entity has no tickets!");
        return response;
    }


    @Path("/{vorstellungid}/tickets")
    @RolesAllowed({"USER", "EMPLOYEE", "ADMIN"})
    @POST
    public Response createTicketsForShowing(
            @PathParam("vorstellungid") int vorstellungRowId,
            @FormDataParam("kind") boolean istKinderticket,
            @FormDataParam("reihe") String sitzreihe,
            @FormDataParam("nummer") int platznummer,
            @FormDataParam("benutzerid") int benutzerRowId
    ) throws NotFoundException, BadRequestException {
        if (vorstellungRowId < 1) throw new NotFoundException("Not Found: Entity does not exist!");
        if (sitzreihe == null) throw new BadRequestException("Bad Request: 'reihe' missing!");

        // Get vorstellung data
        int vorstellungFilmeId;
        String vorstellungSaeleName;
        String vorstellungDatumUhrzeit;
        try {
            String querySelectVorstellung = "SELECT * FROM vorstellungen WHERE rowid = " + vorstellungRowId + ";\n";
            ResultSet resultSet = dataSource.getConnection().createStatement().executeQuery(querySelectVorstellung);

            vorstellungFilmeId = resultSet.getInt("filme_id");
            vorstellungSaeleName = resultSet.getString("saele_name");
            vorstellungDatumUhrzeit = resultSet.getString("datum_uhrzeit");
        }
        catch (SQLException e) { throw new BadRequestException(e.getMessage()); }

        // Insert new ticket and benutzer_reservieren_tickets
        int ticketRowId;
        try (Connection connection = dataSource.getConnection()) {
            String query = "BEGIN TRANSACTION;\n";
            query += String.format(
                    "INSERT INTO tickets VALUES (%d, '%s', '%s', '%s', %d, %b);\n",
                    vorstellungFilmeId, vorstellungSaeleName, vorstellungDatumUhrzeit, sitzreihe, platznummer, istKinderticket
            );
            query += String.format(
                    "INSERT INTO benutzer_reservieren_tickets VALUES (%d, '%s', '%s', '%s', %d, '%s');\n",
                    vorstellungFilmeId, vorstellungSaeleName, vorstellungDatumUhrzeit, sitzreihe, platznummer,
                    securityContext.getUserPrincipal().getName()
            );

            int success = connection.createStatement().executeUpdate(query);
            if (success > 0) connection.createStatement().executeUpdate("END TRANSACTION;\n");
            else connection.createStatement().executeUpdate("ROLLBACK;\n");

            // Get new ticket rowid
            ticketRowId = connection.createStatement().executeQuery(String.format(
                    "SELECT rowid AS 'rowid' FROM tickets WHERE vorstellungen_filme_id = %d AND " +
                    "vorstellungen_saele_name = '%s' AND vorstellungen_datum_uhrzeit = '%s' AND " +
                    "sitzreihe = '%s' AND platznummer = %d;\n",
                    vorstellungFilmeId, vorstellungSaeleName, vorstellungDatumUhrzeit, sitzreihe, platznummer
            )).getInt("rowid");
        }
        catch (SQLException ex) { throw new BadRequestException(ex.getMessage()); }

        return Response.created(
                uriInfo.getBaseUriBuilder()
                        .path("/tickets/"+ticketRowId)
                        .build()
        )
        .build();
    }
}
