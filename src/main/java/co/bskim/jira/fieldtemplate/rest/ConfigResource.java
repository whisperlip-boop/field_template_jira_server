package co.bskim.jira.fieldtemplate.rest;

import co.bskim.jira.fieldtemplate.rest.dto.InitConfigDto;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** 관리자 화면 초기화에 필요한 참조 데이터(이슈타입/역할/프로젝트/색상 팔레트). */
@Path("/config")
@Produces(MediaType.APPLICATION_JSON)
public class ConfigResource {

    /** 원본과 동일 팔레트 재현이 아니라 자체 선정한 기본 팔레트. */
    private static final List<String> COLORS = Arrays.asList(
            "#DEEBFF", "#E3FCEF", "#FFFAE6", "#FFEBE6", "#EAE6FF", "#F4F5F7", "#E6FCFF", "#FFE6F7",
            "#D77975", "#61955E", "#2E64DC", "#EFA144", "#8A92D5"
    );

    private final ConstantsManager constantsManager;
    private final ProjectRoleManager projectRoleManager;
    private final ProjectManager projectManager;
    private final PermissionManager permissionManager;
    private final JiraAuthenticationContext authenticationContext;

    @Inject
    public ConfigResource(@ComponentImport ConstantsManager constantsManager,
                           @ComponentImport ProjectRoleManager projectRoleManager,
                           @ComponentImport ProjectManager projectManager,
                           @ComponentImport PermissionManager permissionManager,
                           @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.constantsManager = constantsManager;
        this.projectRoleManager = projectRoleManager;
        this.projectManager = projectManager;
        this.permissionManager = permissionManager;
        this.authenticationContext = authenticationContext;
    }

    @GET
    public Response get() {
        ApplicationUser user = authenticationContext.getLoggedInUser();

        InitConfigDto dto = new InitConfigDto();
        dto.issueTypes = constantsManager.getAllIssueTypeObjects().stream()
                .map(it -> new InitConfigDto.Entry(it.getId(), it.getName()))
                .collect(Collectors.toList());

        dto.roles = projectRoleManager.getProjectRoles().stream()
                .map(r -> new InitConfigDto.RoleEntry(r.getId(), r.getName()))
                .collect(Collectors.toList());

        dto.projects = projectManager.getProjectObjects().stream()
                .filter(p -> permissionManager.hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, p, user))
                .map(p -> new InitConfigDto.Entry(p.getKey(), p.getName()))
                .collect(Collectors.toList());

        dto.colors = COLORS;

        return Response.ok(dto).build();
    }
}
