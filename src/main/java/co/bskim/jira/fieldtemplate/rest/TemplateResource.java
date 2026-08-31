package co.bskim.jira.fieldtemplate.rest;

import co.bskim.jira.fieldtemplate.model.Template;
import co.bskim.jira.fieldtemplate.model.TemplateIssueType;
import co.bskim.jira.fieldtemplate.model.TemplateRestriction;
import co.bskim.jira.fieldtemplate.model.TemplateRole;
import co.bskim.jira.fieldtemplate.rest.dto.RestrictionDto;
import co.bskim.jira.fieldtemplate.rest.dto.TemplateDto;
import co.bskim.jira.fieldtemplate.service.TemplateService;
import co.bskim.jira.fieldtemplate.util.RestrictionType;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Path("/templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TemplateResource {

    private final TemplateService templateService;
    private final ProjectManager projectManager;
    private final PermissionManager permissionManager;
    private final JiraAuthenticationContext authenticationContext;
    private final UserManager userManager;

    @Inject
    public TemplateResource(TemplateService templateService,
                             @ComponentImport ProjectManager projectManager,
                             @ComponentImport PermissionManager permissionManager,
                             @ComponentImport JiraAuthenticationContext authenticationContext,
                             @ComponentImport UserManager userManager) {
        this.templateService = templateService;
        this.projectManager = projectManager;
        this.permissionManager = permissionManager;
        this.authenticationContext = authenticationContext;
        this.userManager = userManager;
    }

    @GET
    @Path("/project/{projectKey}")
    public Response listByProject(@PathParam("projectKey") String projectKey) {
        Response denied = requireProjectAdmin(projectKey);
        if (denied != null) {
            return denied;
        }
        List<TemplateDto> dtos = templateService.findByProject(projectKey).stream()
                .map(this::toDto).collect(Collectors.toList());
        return Response.ok(dtos).build();
    }

    @GET
    @Path("/project/{projectKey}/field/{fieldId}")
    public Response listByProjectAndField(@PathParam("projectKey") String projectKey, @PathParam("fieldId") String fieldId) {
        Response denied = requireProjectAdmin(projectKey);
        if (denied != null) {
            return denied;
        }
        List<TemplateDto> dtos = templateService.findByProjectAndField(projectKey, fieldId).stream()
                .map(this::toDto).collect(Collectors.toList());
        return Response.ok(dtos).build();
    }

    @POST
    public Response create(TemplateDto dto) {
        Response denied = requireProjectAdmin(dto.projectKey);
        if (denied != null) {
            return denied;
        }
        Response invalid = validateTitle(dto);
        if (invalid != null) {
            return invalid;
        }
        Template created = templateService.create(toInput(dto));
        return Response.status(Response.Status.CREATED).entity(toDto(created)).build();
    }

    @PUT
    @Path("/{templateId}")
    public Response update(@PathParam("templateId") int templateId, TemplateDto dto) {
        Template existing = templateService.findById(templateId);
        Response denied = requireProjectAdmin(existing.getProjectKey());
        if (denied != null) {
            return denied;
        }
        Response invalid = validateTitle(dto);
        if (invalid != null) {
            return invalid;
        }
        Template updated = templateService.update(templateId, toInput(dto));
        return Response.ok(toDto(updated)).build();
    }

    /**
     * Template.title은 AO에서 @NotNull인데, AO는 null뿐 아니라 빈 문자열("")도 거부하면서
     * IllegalArgumentException을 던져 500으로 노출시킨다("Cannot set non-null String field TITLE to
     * ''") — 클라이언트가 빈 제목으로 저장을 시도한 경우(New Template 폼에 아무것도 안 채우고 Save를
     * 누르는 등) 여기서 먼저 걸러서 이해 가능한 400을 준다.
     */
    private Response validateTitle(TemplateDto dto) {
        if (dto.title == null || dto.title.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorMessage("Title is required.")).build();
        }
        return null;
    }

    private static final class ErrorMessage {
        public String message;

        ErrorMessage(String message) {
            this.message = message;
        }
    }

    @DELETE
    @Path("/{templateId}")
    public Response delete(@PathParam("templateId") int templateId) {
        Template existing = templateService.findById(templateId);
        Response denied = requireProjectAdmin(existing.getProjectKey());
        if (denied != null) {
            return denied;
        }
        templateService.delete(templateId);
        return Response.noContent().build();
    }

    @PUT
    @Path("/project/{projectKey}/field/{fieldId}/order")
    public Response reorder(@PathParam("projectKey") String projectKey, @PathParam("fieldId") String fieldId, List<Integer> orderedTemplateIds) {
        Response denied = requireProjectAdmin(projectKey);
        if (denied != null) {
            return denied;
        }
        templateService.reorder(projectKey, fieldId, orderedTemplateIds);
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

    private TemplateDto toDto(Template template) {
        TemplateDto dto = new TemplateDto();
        dto.id = template.getID();
        dto.projectKey = template.getProjectKey();
        dto.fieldId = template.getFieldId();
        dto.title = template.getTitle();
        dto.color = template.getColor();
        dto.text = template.getText();
        dto.visible = Boolean.TRUE.equals(template.getVisible());
        dto.isDefault = Boolean.TRUE.equals(template.getIsDefault());
        dto.sortOrder = template.getSortOrder();
        dto.usageCount = template.getUsageCount();
        dto.groupId = template.getGroup() == null ? null : template.getGroup().getID();
        dto.screenTypes = csvToSet(template.getScreenTypes());
        dto.issueTypeIds = Arrays.stream(template.getIssueTypes())
                .map(TemplateIssueType::getIssueTypeId).collect(Collectors.toSet());
        dto.roleIds = Arrays.stream(template.getRoles())
                .map(TemplateRole::getRoleId).collect(Collectors.toSet());
        dto.restrictions = Arrays.stream(template.getRestrictions())
                .map(r -> new RestrictionDto(r.getType(), r.getTargetKey(), resolveTargetLabel(r.getType(), r.getTargetKey())))
                .collect(Collectors.toList());
        return dto;
    }

    /** USER 제한은 targetKey가 Jira 내부 사용자 키라서 화면에 그대로 보여주면 알아볼 수 없다 — 표시 이름으로 바꿔준다. */
    private String resolveTargetLabel(RestrictionType type, String targetKey) {
        if (type != RestrictionType.USER || targetKey == null) {
            return null;
        }
        ApplicationUser user = userManager.getUserByKey(targetKey);
        if (user == null) {
            return null;
        }
        return user.getDisplayName() + " (" + user.getUsername() + ")";
    }

    private TemplateService.TemplateInput toInput(TemplateDto dto) {
        TemplateService.TemplateInput input = new TemplateService.TemplateInput();
        input.projectKey = dto.projectKey;
        input.fieldId = dto.fieldId;
        input.title = dto.title;
        input.color = dto.color;
        input.text = dto.text;
        input.visible = dto.visible;
        input.isDefault = dto.isDefault;
        input.groupId = dto.groupId;
        input.screenTypes = dto.screenTypes;
        input.issueTypeIds = dto.issueTypeIds;
        input.roleIds = dto.roleIds;
        input.restrictions = dto.restrictions == null ? new ArrayList<>() : dto.restrictions.stream()
                .map(r -> new TemplateService.RestrictionInput(r.type, r.targetKey)).collect(Collectors.toList());
        return input;
    }

    private java.util.Set<String> csvToSet(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return java.util.Collections.emptySet();
        }
        return new java.util.LinkedHashSet<>(Arrays.asList(csv.split(",")));
    }
}
