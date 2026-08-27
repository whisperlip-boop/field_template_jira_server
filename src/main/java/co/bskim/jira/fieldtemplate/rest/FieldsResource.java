package co.bskim.jira.fieldtemplate.rest;

import co.bskim.jira.fieldtemplate.service.FieldDiscoveryService;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/fields")
@Produces(MediaType.APPLICATION_JSON)
public class FieldsResource {

    private final FieldDiscoveryService fieldDiscoveryService;

    @Inject
    public FieldsResource(FieldDiscoveryService fieldDiscoveryService) {
        this.fieldDiscoveryService = fieldDiscoveryService;
    }

    @GET
    public Response findTextFields(@QueryParam("projectKey") String projectKey, @QueryParam("issueTypeId") String issueTypeId) {
        return Response.ok(fieldDiscoveryService.findTextFields(projectKey, issueTypeId)).build();
    }
}
