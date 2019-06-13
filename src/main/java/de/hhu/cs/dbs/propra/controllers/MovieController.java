package de.hhu.cs.dbs.propra.controllers;

import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.sql.*;
import java.util.*;

@Path("/filme")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class MovieController {
    @Inject
    private DataSource dataSource;

    @Context
    private SecurityContext securityContext;

    @Context
    private UriInfo uriInfo;

    @GET
    public List<Map<String, Object>> getMovies(
            @QueryParam("titel") String titel,
            @QueryParam("genre") List<String> listOfGenre,
            @QueryParam("jahr") int jahr,
            @QueryParam("schauspieler") List<String> listOfSchauspieler
    ) throws NotFoundException {
        // Prepare query
        StringBuilder queryBuilder = new StringBuilder(
                "SELECT F.rowid AS 'rowid', F.*\n" +
                "FROM filme F"
        );

        if (titel != null || jahr > 0 || !listOfGenre.isEmpty() || !listOfSchauspieler.isEmpty()) {
            if (!listOfSchauspieler.isEmpty()) {
                queryBuilder.append(", personen P, schauspieler S, schauspieler_mitspielen_filme SF ")
                        .append("\nWHERE F.id = SF.filme_id AND ")
                        .append("SF.schauspieler_personen_id = S.personen_id AND ")
                        .append("S.personen_id = P.id AND ");
            }
            else queryBuilder.append("\nWHERE ");

            listOfSchauspieler.forEach(
                    schauspieler ->queryBuilder.append("(P.vorname LIKE LOWER('%").append(schauspieler).append("%') OR ")
                            .append("P.nachname LIKE LOWER('%").append(schauspieler).append("%') OR ")
                            .append("S.kuenstlername LIKE LOWER('%").append(schauspieler).append("%')) AND ")
            );
            listOfGenre.forEach(
                    genre -> queryBuilder.append(
                            String.format(
                                    "LOWER('%s') IN (SELECT LOWER(genre_genretitel) FROM filme_angehoeren_genre WHERE F.id = filme_id) AND ",
                                    genre
                            )
                    )
            );
            if (titel != null)
                queryBuilder.append("LOWER(F.titel) LIKE LOWER('%").append(titel).append("%') AND ");
            if (jahr > 0) {
                queryBuilder.append("STRFTIME('%Y', F.veroeffentlichungs_datum) == '").append(jahr).append("' AND ");
            }
            queryBuilder.delete(queryBuilder.lastIndexOf("AND "), queryBuilder.length());
        }
        queryBuilder.append(";\n");

        List<Map<String, Object>> response = new ArrayList<>();
        Map<String, Object> entity;
        try (Connection connection = dataSource.getConnection()) {
            ResultSet resultSet = connection.createStatement().executeQuery(queryBuilder.toString());
            while (resultSet.next()) {
                entity = new HashMap<>();
                entity.put("filmid", resultSet.getInt("rowid"));
                entity.put("titel", resultSet.getString("titel"));
                entity.put("text", resultSet.getString("beschreibung"));
                entity.put("laenge", resultSet.getInt("spiellaenge"));
                entity.put("datum", resultSet.getString("veroeffentlichungs_datum"));
                entity.put("fsk", resultSet.getInt("fsk"));
                entity.put("cover", resultSet.getObject("cover"));
                entity.put("trailer", resultSet.getString("trailer"));
                response.add(entity);
            }

            resultSet.close();
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        if (response.isEmpty()) throw new NotFoundException("Not Found: Entities do not exist!");
        return response;
    }


    @RolesAllowed({"EMPLOYEE", "ADMIN"})
    @POST
    public Response createMovie(
            @FormDataParam("titel") String titel,
            @FormDataParam("text") String text,
            @FormDataParam("laenge") int laenge,
            @FormDataParam("datum") String datum,
            @FormDataParam("fsk") int fsk,
            @FormDataParam("cover") InputStream cover,
            @FormDataParam("trailer") String trailer
    ) throws BadRequestException {
        if (titel == null) throw new BadRequestException("Bad Request: 'titel' missing!");
        if (text == null) throw new BadRequestException("Bad Request: 'text' missing!");
        if (laenge <= 0) throw new BadRequestException("Bad Request: 'laenge' missing!");
        if (datum == null) throw new BadRequestException("Bad Request: 'datum' missing!");
        if (fsk <= 0) throw new BadRequestException("Bad Request: 'fsk' missing!");
        if (cover == null) throw new BadRequestException("Bad Request: 'cover' missing!");
        if (trailer == null) throw new BadRequestException("Bad Request: 'trailer' missing!");

        int rowId;
        try (
                Connection connection = dataSource.getConnection();
                cover
        ) {
            // Find filmeId of new film
            String queryGetFilmeId = "SELECT count(*) AS 'anzahl' FROM filme;\n";
            int filmeId = connection.createStatement()
                    .executeQuery(queryGetFilmeId)
                    .getInt("anzahl") + 1;

            String query = "INSERT INTO filme VALUES (?, ?, ?, ?, ?, ?, ?, DATETIME(?));\n";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.closeOnCompletion();
            preparedStatement.setInt(1, filmeId);
            preparedStatement.setString(2, trailer);
            preparedStatement.setBytes(3, cover.readAllBytes());
            preparedStatement.setInt(4, fsk);
            preparedStatement.setString(5, text);
            preparedStatement.setInt(6, laenge);
            preparedStatement.setString(7, titel);
            preparedStatement.setString(8, datum);
            preparedStatement.executeUpdate();

            // Get rowId of new film
            rowId = connection.createStatement()
                    .executeQuery("SELECT F.rowid AS 'rowid' FROM filme F WHERE id = " + filmeId + ";")
                    .getInt("rowid");
        }
        catch (SQLException | IOException ex)  { throw new BadRequestException(ex.getMessage()); }

        return Response.created(
                uriInfo.getAbsolutePathBuilder()
                        .path("/" + rowId)
                        .build()
        )
        .build();
    }


    @Path("/{filmid}")
    @GET
    public Map<String, Object> getMovie(
            @PathParam("filmid") int filmRowId
    ) throws NotFoundException {
        if (filmRowId < 1) throw new NotFoundException("Not Found: Entity does not exist");

        // Prepare query
        String query = String.format(
                "SELECT F.rowid AS 'rowid', F.titel, F.beschreibung, F.spiellaenge, F.veroeffentlichungs_datum, F.fsk, F.cover, F.trailer\n" +
                "FROM filme F\n" +
                "WHERE rowid = %d;\n",
                filmRowId
        );

        Map<String, Object> response = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            ResultSet resultSet = connection.createStatement().executeQuery(query);

            // Fill response
            while (resultSet.next()) {
                response.put("filmid", resultSet.getInt("rowid"));
                response.put("titel", resultSet.getString("titel"));
                response.put("text", resultSet.getString("beschreibung"));
                response.put("laenge", resultSet.getInt("spiellaenge"));
                response.put("datum", resultSet.getString("veroeffentlichungs_datum"));
                response.put("fsk", resultSet.getInt("fsk"));
                response.put("cover", resultSet.getObject("cover"));
                response.put("trailer", resultSet.getString("trailer"));
            }

            resultSet.close();
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        if (response.isEmpty()) throw new NotFoundException("Not Found: Entity does not exist!");
        return response;
    }


    @Path("/{filmid}")
    @RolesAllowed({"EMPLOYEE", "ADMIN"})
    @PATCH
    public Response updateMovie(
            @PathParam("filmid") int filmRowId,
            @FormDataParam("titel") String titel,
            @FormDataParam("text") String text,
            @FormDataParam("laenge") int laenge,
            @FormDataParam("datum") String datum,
            @FormDataParam("fsk") int fsk,
            @FormDataParam("cover") InputStream cover,
            @FormDataParam("trailer") String trailer
    ) throws SQLException, NotFoundException, BadRequestException, InternalServerErrorException {
        if (filmRowId < 1) throw new NotFoundException("Not Found: Entity does not exist!");

        Connection  connection = dataSource.getConnection();

        // Prepare updates
        int reqFilmeId;
        try {
            reqFilmeId = connection
                    .createStatement()
                    .executeQuery("SELECT id FROM filme WHERE rowid = '" + filmRowId + "';")
                    .getInt("id");
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exist!"); }

        String queryUpdateFilm = "";
        if (titel != null || text != null || laenge > 0 || datum != null || fsk > 0 || trailer != null) {
            queryUpdateFilm = "UPDATE filme SET ";
            if (titel != null) queryUpdateFilm += String.format("titel = '%s', ", titel);
            if (text != null) queryUpdateFilm += String.format("beschreibung = '%s', ", text);
            if (laenge > 0) queryUpdateFilm += String.format("spiellaenge = %d, ", laenge);
            if (datum != null) queryUpdateFilm += String.format("veroeffentlichungs_datum = DATETIME('%s'), ", datum);
            if (fsk > 0) queryUpdateFilm += String.format("fsk = %d, ", fsk);
            if (trailer != null) queryUpdateFilm += String.format("trailer = '%s', ", trailer);
            queryUpdateFilm = queryUpdateFilm.substring(0, queryUpdateFilm.lastIndexOf(", "));
            queryUpdateFilm += " ";
            queryUpdateFilm += "WHERE id = " + reqFilmeId + ";\n";
        }

        try (
                connection;
                cover
        ) {
            int success;

            String query = "BEGIN TRANSACTION;\n"  + queryUpdateFilm;
            success = connection.createStatement().executeUpdate(query);

            if (cover != null) {
                String queryUpdateCover = "UPDATE filme SET cover = ? WHERE id = ?; ";
                PreparedStatement preparedStatement = connection.prepareStatement(queryUpdateCover);
                preparedStatement.closeOnCompletion();
                preparedStatement.setBytes(1, cover.readAllBytes());
                preparedStatement.setInt(2, reqFilmeId);
                success = preparedStatement.executeUpdate();
            }

            if (success > 0) connection.createStatement().executeUpdate("END TRANSACTION;\n");
            else connection.createStatement().executeUpdate("ROLLBACK;\n");
        }
        catch (SQLException | IOException ex) { throw new BadRequestException(ex.getMessage()); }

        // Return result
        return Response.status(Response.Status.NO_CONTENT).build();
    }


    @Path("/{filmid}")
    @RolesAllowed({"EMPLOYEE", "ADMIN"})
    @DELETE
    public Response deleteMovie(
            @PathParam("filmid") int filmRowId
    ) throws NotFoundException {
        if (filmRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        try (Connection  connection = dataSource.getConnection()) {
            String query = String.format(
                    "BEGIN TRANSACTION;\n" +
                    "DELETE FROM filme WHERE rowid = %d;\n" +
                    "END TRANSACTION;\n",
                    filmRowId
            );
            connection.createStatement().executeUpdate(query);
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        // Return result
        return Response.status(Response.Status.NO_CONTENT).build();
    }


    @Path("/{filmid}/genres")
    @GET
    public List<Map<String, Object>> getGenresForMovie(
            @PathParam("filmid") int filmRowId
    ) throws NotFoundException, SQLException {
        if (filmRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        Connection connection = dataSource.getConnection();

        // Prepare query
        int filmId;
        try {
            String querySelectMovieWithRowId = String.format("SELECT id FROM filme WHERE rowid = %d;", filmRowId);
            filmId = connection.createStatement().executeQuery(querySelectMovieWithRowId).getInt("id");
        }
        catch (SQLException e) { throw new NotFoundException("Not Found: Entity does not exits!"); }

        List<Map<String, Object>> response = new ArrayList<>();
        try (connection) {
            String query = String.format(
                    "SELECT G.rowid AS 'rowid', G.genretitel " +
                    "FROM genre G, filme_angehoeren_genre FG " +
                    "WHERE FG.filme_id = %d AND FG.genre_genretitel = G.genretitel;",
                    filmId
            );

            ResultSet resultSet = connection.createStatement().executeQuery(query);
            Map<String, Object> entity;
            while (resultSet.next()) {
                entity = new HashMap<>();
                entity.put("genreid", resultSet.getString("rowid"));
                entity.put("bezeichnung", resultSet.getString("genretitel"));
                response.add(entity);
            }

            resultSet.close();
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        if (response.isEmpty()) throw new NotFoundException("Not Found: Entity has no genre!");
        return response;
    }


    @Path("/{filmid}/genres")
    @RolesAllowed({"EMPLOYEE", "ADMIN"})
    @POST
    public Response createGenreForMovie(
            @PathParam("filmid") int filmRowId,
            @FormDataParam("genreid") int genreRowId
    ) throws NotFoundException, BadRequestException {
        if (filmRowId < 1) throw new NotFoundException("Not Found: Entity does not exist!");
        if (genreRowId < 1) throw new BadRequestException("Bad Request: 'genreid' missing!");

        int filmId;
        String genretitel;
        try {
            filmId = dataSource.getConnection()
                    .createStatement()
                    .executeQuery("SELECT id FROM filme WHERE rowid = " + filmRowId + ";\n")
                    .getInt("id");

            genretitel = dataSource.getConnection()
                    .createStatement()
                    .executeQuery("SELECT genretitel FROM genre WHERE rowid = " + genreRowId + ";\n")
                    .getString("genretitel");
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exits!"); }

        try (Connection connection = dataSource.getConnection()) {
            String query = "INSERT INTO filme_angehoeren_genre VALUES (?, ?);\n";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.closeOnCompletion();
            preparedStatement.setInt(1, filmId);
            preparedStatement.setString(2, genretitel);
            preparedStatement.executeUpdate();
        }
        catch (SQLException ex)  { throw new BadRequestException(ex.getMessage()); }

        return Response.created(
                uriInfo.getAbsolutePathBuilder()
                        .build()
        )
        .build();
    }


    @Path("/{filmid}/genres/{genreid}")
    @RolesAllowed({"EMPLOYEE", "ADMIN"})
    @DELETE
    public Response deleteGenreForMovie(
            @PathParam("filmid") int filmRowId,
            @PathParam("genreid") int genreRowId
    ) throws NotFoundException {
        if (filmRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");
        if (genreRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        int filmId;
        String genretitel;
        try {
            filmId = dataSource.getConnection().createStatement().executeQuery("SELECT id FROM filme WHERE rowid = " + filmRowId + ";\n")
                    .getInt("id");

            genretitel = dataSource.getConnection().createStatement().executeQuery("SELECT genretitel FROM genre WHERE rowid = " + genreRowId + ";\n")
                    .getString("genretitel");
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exits!"); }

        try (Connection connection = dataSource.getConnection()) {
            String query = String.format(
                    "DELETE FROM filme_angehoeren_genre WHERE filme_id = %d AND genre_genretitel = '%s';\n",
                    filmId, genretitel
            );
            connection.createStatement().executeUpdate(query);
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        // Return result
        return Response.status(Response.Status.NO_CONTENT).build();
    }


    @Path("/{filmid}/schauspieler")
    @GET
    public List<Map<String, Object>> getActorsForMovie(
            @PathParam("filmid") int filmRowId
    ) throws NotFoundException {
        if (filmRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        // Prepare query
        int filmId;
        try {
            String querySelectMovieWithRowId = String.format("SELECT id FROM filme WHERE rowid = %d;", filmRowId);
            Connection connection = dataSource.getConnection();
            filmId = connection.createStatement().executeQuery(querySelectMovieWithRowId).getInt("id");
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exits!"); }

        List<Map<String, Object>> response = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            String query = String.format(
                    "SELECT S.rowid AS 'schauspieler_rowid', P.vorname, P.nachname, P.geburtsdatum, S.kuenstlername\n" +
                    "FROM schauspieler_mitspielen_filme SF, filme F, schauspieler S, personen P\n" +
                    "WHERE F.rowid = %d AND F.id = SF.filme_id AND SF.schauspieler_personen_id = S.personen_id AND S.personen_id = P.id;",
                    filmId
            );

            ResultSet resultSet = connection.createStatement().executeQuery(query);
            Map<String, Object> entity;
            while (resultSet.next()) {
                entity = new HashMap<>();
                entity.put("schauspielerid", resultSet.getInt("schauspieler_rowid"));
                entity.put("vorname", resultSet.getString("vorname"));
                entity.put("nachname", resultSet.getString("nachname"));
                entity.put("geburtsdatum", resultSet.getString("geburtsdatum"));
                entity.put("kuenstlername", resultSet.getString("kuenstlername"));
                response.add(entity);
            }

            resultSet.close();
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        if (response.isEmpty()) throw new NotFoundException("Not Found: Entity has no actors!");
        return response;
    }


    @Path("/{filmid}/schauspieler")
    @RolesAllowed({"EMPLOYEE", "ADMIN"})
    @POST
    public Response createActorForMovie(
            @PathParam("filmid") int filmRowId,
            @FormDataParam("schauspielerid") int schauspielerRowId
    ) throws NotFoundException, BadRequestException {
        if (filmRowId < 1) throw new NotFoundException("Not Found: Entity does not exist!");
        if (schauspielerRowId < 1) throw new BadRequestException("Bad Request: 'schauspielerid' missing!");

        int filmId;
        int schauspielerPersonenId;
        try {
            filmId = dataSource.getConnection()
                    .createStatement()
                    .executeQuery("SELECT id FROM filme WHERE rowid = " + filmRowId + ";\n")
                    .getInt("id");

            schauspielerPersonenId = dataSource.getConnection()
                    .createStatement()
                    .executeQuery("SELECT personen_id FROM schauspieler WHERE rowid = " + schauspielerRowId + ";\n")
                    .getInt("personen_id");
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exits!"); }

        try (Connection connection = dataSource.getConnection()) {
            String query = "INSERT INTO schauspieler_mitspielen_filme VALUES (?, ?);\n";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.closeOnCompletion();
            preparedStatement.setInt(1, schauspielerPersonenId);
            preparedStatement.setInt(2, filmId);
            preparedStatement.executeUpdate();
        }
        catch (SQLException ex)  { throw new BadRequestException(ex.getMessage()); }

        return Response.created(
                uriInfo.getAbsolutePathBuilder()
                        .build()
        )
        .build();
    }


    @Path("/{filmid}/schauspieler/{schauspielerid}")
    @RolesAllowed({"EMPLOYEE", "ADMIN"})
    @DELETE
    public Response deleteActorForMovie(
            @PathParam("filmid") int filmRowId,
            @PathParam("schauspielerid") int schauspielerRowId
    ) throws NotFoundException {
        if (filmRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");
        if (schauspielerRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        int filmId;
        int schauspielerPersonenId;
        try {
            filmId = dataSource.getConnection()
                    .createStatement()
                    .executeQuery("SELECT id FROM filme WHERE rowid = " + filmRowId + ";\n")
                    .getInt("id");

            schauspielerPersonenId = dataSource.getConnection()
                    .createStatement()
                    .executeQuery("SELECT personen_id FROM schauspieler WHERE rowid = " + schauspielerRowId + ";\n")
                    .getInt("personen_id");
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exits!"); }

        try (Connection connection = dataSource.getConnection()) {
            String query = String.format(
                    "DELETE FROM schauspieler_mitspielen_filme WHERE filme_id = %d AND schauspieler_personen_id = %d;\n",
                    filmId, schauspielerPersonenId
            );
            connection.createStatement().executeUpdate(query);
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        // Return result
        return Response.status(Response.Status.NO_CONTENT).build();
    }


    @Path("/{filmid}/bewertungen")
    @GET
    public Map<String, Object> getRatingsForMovie(
            @PathParam("filmid") int filmRowId
    ) throws NotFoundException {
        if (filmRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");

        // Prepare query
        int filmId;
        try {
            String querySelectMovieWithRowId = String.format("SELECT id FROM filme WHERE rowid = %d;", filmRowId);
            Connection connection = dataSource.getConnection();
            filmId = connection.createStatement()
                    .executeQuery(querySelectMovieWithRowId)
                    .getInt("id");
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exits!"); }

        Map<String, Object> response = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            String query = String.format(
                    "SELECT BBF.average_rating AS 'average_rating', BBF.number_of_ratings AS 'number_of_ratings'\n" +
                    "FROM (\n" +
                    "  SELECT BBF.filme_id, AVG(BBF.sterne) AS 'average_rating',\n" +
                    "         COUNT(BBF.filme_id) AS 'number_of_ratings'\n" +
                    "  FROM benutzer_bewerten_filme BBF\n" +
                    "  GROUP BY BBF.filme_id\n" +
                    ") BBF, filme F\n" +
                    "WHERE F.id = %d AND F.id = BBF.filme_id\n" +
                    "GROUP BY BBF.filme_id;",
                    filmId
            );

            ResultSet resultSet = connection.createStatement().executeQuery(query);
            while (resultSet.next()) {
                response.put("anzahl", resultSet.getInt("number_of_ratings"));
                response.put("durchschnitt", resultSet.getInt("average_rating"));
            }

            resultSet.close();
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        if (response.isEmpty()) throw new NotFoundException("Not Found: Entity has no ratings!");
        return response;
    }


    @Path("/{filmid}/bewertungen")
    @RolesAllowed({"USER", "EMPLOYEE", "ADMIN"})
    @POST
    public Response createRatingForMovie(
            @PathParam("filmid") int filmRowId,
            @FormDataParam("sterne") int sterne
    ) throws NotFoundException, BadRequestException {
        if (filmRowId < 1) throw new NotFoundException("Not Found: Entity does not exist!");
        if (sterne < 1) throw new BadRequestException("Bad Request: 'sterne' missing!");
        if (sterne > 10) throw new BadRequestException("Bad Request: 'sterne' invalid!");

        int filmId;
        try {
            filmId = dataSource.getConnection()
                    .createStatement()
                    .executeQuery("SELECT id FROM filme WHERE rowid = " + filmRowId + ";\n")
                    .getInt("id");
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exits!"); }

        //int bewertungRowId;
        try (Connection connection = dataSource.getConnection()) {
            String query = "INSERT INTO benutzer_bewerten_filme VALUES (?, ?, ?);\n";
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.closeOnCompletion();
            preparedStatement.setString(1, securityContext.getUserPrincipal().getName());
            preparedStatement.setInt(2, filmId);
            preparedStatement.setInt(3, sterne);
            preparedStatement.executeUpdate();
        }
        catch (SQLException ex)  { throw new BadRequestException(ex.getMessage()); }

        return Response.created(
                uriInfo.getAbsolutePathBuilder()
                        .build()
        )
        .build();
    }


    @Path("/{filmid}/vorstellungen")
    @GET
    public List<Map<String, Object>> getShowingsForMovie(
            @PathParam("filmid") int filmRowId,
            @QueryParam("wochentag") @DefaultValue("0") int wochentag ,
            @QueryParam("sprache") String sprache,
            @QueryParam("dreid") @DefaultValue("false") boolean dreiD
    ) throws NotFoundException, BadRequestException {
        if (filmRowId < 1) throw new NotFoundException("Not Found: Entity does not exits!");
        if (wochentag < 0 || wochentag > 6) throw new BadRequestException("Bad Request: 'wochentag' is invalid!");

        // Prepare query
        int filmId;
        try {
            String querySelectMovieWithRowId = String.format("SELECT id FROM filme WHERE rowid = %d;", filmRowId);
            Connection connection = dataSource.getConnection();
            filmId = connection.createStatement().executeQuery(querySelectMovieWithRowId).getInt("id");
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exits!"); }


        List<Map<String, Object>> response = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            String query =
                    "SELECT V.rowid AS 'vorstellungen_rowid', V.ist_drei_d, V.sprache, V.datum_uhrzeit, S.rowid AS 'saele_rowid' " +
                    "FROM   vorstellungen V, saele S " +
                    "WHERE  V.filme_id = " + filmId + " AND " +
                    "V.saele_name = S.name AND " +
                    "STRFTIME('%w', V.datum_uhrzeit) == '" + wochentag + "' AND "+
                    "V.ist_drei_d == " + dreiD + " AND " +
                    ((sprache != null) ? "LOWER(V.sprache) == LOWER('" + sprache + "') AND " : "");
            query = query.substring(0, query.lastIndexOf("AND "));
            query += ";";

            ResultSet resultSet = connection.createStatement().executeQuery(query);
            Map<String, Object> entity;
            while (resultSet.next()) {
                entity = new HashMap<>();
                entity.put("vorstellungid", resultSet.getInt("vorstellungen_rowid"));
                entity.put("dreid", resultSet.getBoolean("ist_drei_d"));
                entity.put("sprache", resultSet.getString("sprache"));
                entity.put("zeitstempel", resultSet.getString("datum_uhrzeit"));
                entity.put("filmid", filmRowId);
                entity.put("saalid", resultSet.getInt("saele_rowid"));
                response.add(entity);
            }

            resultSet.close();
        }
        catch (SQLException ex) { throw new NotFoundException(ex.getMessage()); }

        if (response.isEmpty()) {
            throw new NotFoundException("Not Found: Entity has no showings! wochentag = " + wochentag);
        }
        return response;
    }


    @Path("/{filmid}/vorstellungen")
    @RolesAllowed({"EMPLOYEE", "ADMIN"})
    @POST
    public Response createShowingForMovie(
            @PathParam("filmid") int filmRowId,
            @FormDataParam("dreid") @DefaultValue("false") boolean dreiD,
            @FormDataParam("sprache") String sprache,
            @FormDataParam("zeitstempel") String zeitstempel,
            @FormDataParam("saalid") int saalRowId
    ) throws NotFoundException, BadRequestException, SQLException {
        if (filmRowId < 1) throw new NotFoundException("Not Found: Entity does not exist!");
        if (sprache == null) throw new BadRequestException("Bad Request: 'sprache' missing!");
        if (zeitstempel == null) throw new BadRequestException("Bad Request: 'zeitstempel' missing!");
        if (saalRowId < 1) throw new BadRequestException("Bad Request: 'saalid' missing!");

        Connection connection = dataSource.getConnection();

        int filmId;
        String saalName;

        try {
            ResultSet resultSetSelectFilmRowId = connection.createStatement()
                    .executeQuery("SELECT id FROM filme WHERE rowid = " + filmRowId + ";\n");
            filmId = resultSetSelectFilmRowId.getInt("id");
            resultSetSelectFilmRowId.close();

            ResultSet resultSetSelectSaalRowId = connection.createStatement()
                    .executeQuery("SELECT name FROM saele WHERE rowid = " + saalRowId + ";\n");
            saalName = resultSetSelectSaalRowId.getString("name");
            resultSetSelectSaalRowId.close();
        }
        catch (SQLException ex) { throw new NotFoundException("Not Found: Entity does not exits!"); }

        try {
            String query = String.format(
                    "INSERT INTO vorstellungen VALUES (%d, '%s', '%s', '%s', %b);\n",
                    filmId, saalName, zeitstempel, sprache, dreiD
            );
            connection.createStatement().executeUpdate(query);
        }
        catch (SQLException ex) { throw new BadRequestException(ex.getMessage()); }

        int vorstellungRowId;
        try (connection) {
            // Get rowid of new vorstellung
            String query = "SELECT rowid AS 'rowid' FROM vorstellungen WHERE filme_id = " + filmId +
                    " AND saele_name = '" + saalName + "' AND datum_uhrzeit = '" + zeitstempel + "'; ";

            ResultSet resultSet = connection.createStatement().executeQuery(query);
            vorstellungRowId = resultSet.getInt("rowid");

            resultSet.close();
        }
        catch (SQLException ex) { throw new BadRequestException(ex.getMessage()); }

        return Response.created(
                uriInfo.getBaseUriBuilder()
                        .path("/vorstellungen/"+vorstellungRowId)
                        .build()
        )
        .build();
    }
}
