package de.hhu.cs.dbs.propra.controllers;

import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Path("/")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON)
public class ExampleController {
    @Inject
    private DataSource dataSource;

    @Context
    private SecurityContext securityContext;

    @Context
    private UriInfo uriInfo;

    @GET // GET http://localhost:8080
    public String halloWelt() {
        return "Hallo Welt!";
    }

    @Path("foo")
    @RolesAllowed({"USER", "EMPLOYEE", "ADMIN"})
    @GET
    // GET http://localhost:8080/foo => OK, wenn Benutzer die Rolle "USER", "EMPLOYEE" oder "ADMIN" hat. Siehe SQLiteUserRepository.
    public String halloFoo() {
        return "Hallo " + securityContext.getUserPrincipal().getName() + "!";
    }

    @Path("foo2/{bar}")
    @GET // GET http://localhost:8080/foo/xyz
    public String halloFoo2(@PathParam("bar") String bar) {
        if (!bar.equals("foo")) throw new NotFoundException("Resource '" + bar + "' not found");
        return "Hallo " + bar + "!";
    }

    @Path("foo3")
    @GET // GET http://localhost:8080/foo?bar=xyz
    public String halloFoo3(@QueryParam("bar") String bar) {
        return "Hallo " + bar + "!";
    }

    @Path("bar")
    @GET // GET http://localhost:8080/bar => Bad Request; http://localhost/bar?foo=xyz => OK
    public Response halloBar(@QueryParam("foo") String foo) {
        if (foo == null) throw new BadRequestException();
        return Response.status(Response.Status.OK).entity("Hallo Bar!").build();
    }

    @Path("bar2")
    @GET // GET http://localhost:8080/bar2
    public List<Map<String, Object>> halloBar2(@QueryParam("name") @DefaultValue("Max Mustermann") List<String> names) throws SQLException {
        Connection connection = dataSource.getConnection();
        PreparedStatement preparedStatement = connection.prepareStatement("SELECT ?;");
        preparedStatement.closeOnCompletion();
        int random = ThreadLocalRandom.current().nextInt(0, names.size());
        preparedStatement.setObject(1, names.get(random));
        ResultSet resultSet = preparedStatement.executeQuery();
        List<Map<String, Object>> entities = new ArrayList<>();
        Map<String, Object> entity;
        while (resultSet.next()) {
            entity = new HashMap<>();
            entity.put("name", resultSet.getObject(1));
            entities.add(entity);
        }
        resultSet.close();
        connection.close();
        return entities;
    }

    @Path("foo")
    @POST // POST http://localhost:8080/foo
    public Response einUpload(@FormDataParam("name") String name, @FormDataParam("file") InputStream file) {
        if (name == null) return Response.status(Response.Status.BAD_REQUEST).build();
        if (file == null) return Response.status(Response.Status.BAD_REQUEST).build();
        byte[] bytes;
        try {
            bytes = IOUtils.toByteArray(file);
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.created(uriInfo.getAbsolutePathBuilder().path("234235").build()).build();
    }
}
