package co.bskim.jira.fieldtemplate.rest;

import co.bskim.jira.fieldtemplate.service.TemplateCopyService;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/copy")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CopyResource {

    private final TemplateCopyService copyService;
    private final ProjectManager projectManager;
    private final PermissionManager permissionManager;
    private final JiraAuthenticationContext authenticationContext;

    @Inject
    public CopyResource(TemplateCopyService copyService,
                         @ComponentImport ProjectManager projectManager,
                         @ComponentImport PermissionManager permissionManager,
                         @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.copyService = copyService;
        this.projectManager = projectManager;
        this.permissionManager = permissionManager;
        this.authenticationContext = authenticationContext;
    }

    /**
     * Copy 패널의 Source Project 드롭다운용 — targetProjectKey를 관리하는 프로젝트 중 fieldId에 대한
     * 템플릿이 실제로 있는 프로젝트만 골라준다(없는 프로젝트를 목록에 보여줘봐야 항상 "복사할 게 0개"
     * 프리뷰만 나오므로 애초에 안 보이게 함).
     */
    @GET
    @Path("/candidates")
    public Response candidates(@QueryParam("targetProjectKey") String targetProjectKey,
                                @QueryParam("fieldId") String fieldId) {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        Project target = projectManager.getProjectByCurrentKey(targetProjectKey);
        if (target == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!permissionManager.hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, target, user)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        List<String> adminProjectKeys = projectManager.getProjectObjects().stream()
                .filter(p -> !p.getKey().equals(targetProjectKey))
                .filter(p -> permissionManager.hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, p, user))
                .map(Project::getKey)
                .collect(java.util.stream.Collectors.toList());
        return Response.ok(copyService.candidateSourceProjects(adminProjectKeys, fieldId)).build();
    }

    @GET
    @Path("/preview")
    public Response preview(@QueryParam("sourceProjectKey") String sourceProjectKey,
                             @QueryParam("targetProjectKey") String targetProjectKey,
                             @QueryParam("fieldId") String fieldId) {
        Response denied = requireProjectAdminOnBoth(sourceProjectKey, targetProjectKey);
        if (denied != null) {
            return denied;
        }
        return Response.ok(copyService.preview(sourceProjectKey, targetProjectKey, fieldId)).build();
    }

    @POST
    public Response copy(CopyRequest request) {
        Response denied = requireProjectAdminOnBoth(request.sourceProjectKey, request.targetProjectKey);
        if (denied != null) {
            return denied;
        }
        copyService.copy(request.sourceProjectKey, request.targetProjectKey, request.fieldId, request.overwrite);
        return Response.noContent().build();
    }

    private Response requireProjectAdminOnBoth(String sourceProjectKey, String targetProjectKey) {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        for (String projectKey : new String[]{sourceProjectKey, targetProjectKey}) {
            Project project = projectManager.getProjectByCurrentKey(projectKey);
            if (project == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            if (!permissionManager.hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, project, user)) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
        }
        return null;
    }

    public static class CopyRequest {
        public String sourceProjectKey;
        public String targetProjectKey;
        public String fieldId;
        public boolean overwrite;
    }
}
