package co.bskim.jira.fieldtemplate.rest;

import co.bskim.jira.fieldtemplate.model.TemplateGroup;
import co.bskim.jira.fieldtemplate.rest.dto.TemplateGroupDto;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.DBParam;
import net.java.ao.Query;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Path("/groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TemplateGroupResource {

    private final ActiveObjects ao;
    private final ProjectManager projectManager;
    private final PermissionManager permissionManager;
    private final JiraAuthenticationContext authenticationContext;

    @Inject
    public TemplateGroupResource(@ComponentImport ActiveObjects ao,
                                  @ComponentImport ProjectManager projectManager,
                                  @ComponentImport PermissionManager permissionManager,
                                  @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.ao = ao;
        this.projectManager = projectManager;
        this.permissionManager = permissionManager;
        this.authenticationContext = authenticationContext;
    }

    @GET
    @Path("/project/{projectKey}")
    public Response listByProject(@PathParam("projectKey") String projectKey) {
        Response denied = requireProjectAdmin(projectKey);
        if (denied != null) {
            return denied;
        }
        TemplateGroup[] groups = ao.find(TemplateGroup.class, Query.select()
                .where("PROJECT_KEY = ?", projectKey).order("SORT_ORDER ASC"));
        List<TemplateGroupDto> dtos = Arrays.stream(groups).map(this::toDto).collect(Collectors.toList());
        return Response.ok(dtos).build();
    }

    @POST
    public Response create(TemplateGroupDto dto) {
        Response denied = requireProjectAdmin(dto.projectKey);
        if (denied != null) {
            return denied;
        }
        TemplateGroup group = ao.create(TemplateGroup.class,
                new DBParam("PROJECT_KEY", dto.projectKey),
                new DBParam("NAME", dto.name));
        group.setSortOrder(dto.sortOrder == null ? 0 : dto.sortOrder);
        group.save();
        return Response.status(Response.Status.CREATED).entity(toDto(group)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") int id) {
        TemplateGroup group = ao.get(TemplateGroup.class, id);
        if (group == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Response denied = requireProjectAdmin(group.getProjectKey());
        if (denied != null) {
            return denied;
        }
        for (co.bskim.jira.fieldtemplate.model.Template template : group.getTemplates()) {
            template.setGroup(null);
            template.save();
        }
        ao.delete(group);
        return Response.noContent().build();
    }

    private Response requireProjectAdmin(String projectKey) {
        Project project = projectManager.getProjectByCurrentKey(projectKey);
        if (project == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (!permissionManager.hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, project, user)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return null;
    }

    private TemplateGroupDto toDto(TemplateGroup group) {
        TemplateGroupDto dto = new TemplateGroupDto();
        dto.id = group.getID();
        dto.projectKey = group.getProjectKey();
        dto.name = group.getName();
        dto.sortOrder = group.getSortOrder();
        return dto;
    }
}
