package co.bskim.jira.fieldtemplate.rest;

import co.bskim.jira.fieldtemplate.model.PersonalTemplate;
import co.bskim.jira.fieldtemplate.model.PersonalTemplateIssueType;
import co.bskim.jira.fieldtemplate.model.PersonalTemplateProject;
import co.bskim.jira.fieldtemplate.rest.dto.PersonalTemplateDto;
import co.bskim.jira.fieldtemplate.service.PersonalTemplateService;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/personal-templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PersonalTemplateResource {

    private final PersonalTemplateService personalTemplateService;
    private final JiraAuthenticationContext authenticationContext;

    @Inject
    public PersonalTemplateResource(PersonalTemplateService personalTemplateService,
                                     @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.personalTemplateService = personalTemplateService;
        this.authenticationContext = authenticationContext;
    }

    @GET
    public Response listMine() {
        Response denied = requireLoggedIn();
        if (denied != null) {
            return denied;
        }
        List<PersonalTemplateDto> dtos = personalTemplateService.findByUser(currentUser().getKey()).stream()
                .map(this::toDto).collect(Collectors.toList());
        return Response.ok(dtos).build();
    }

    @POST
    public Response create(PersonalTemplateDto dto) {
        Response denied = requireLoggedIn();
        if (denied != null) {
            return denied;
        }
        Response invalid = validateTitle(dto);
        if (invalid != null) {
            return invalid;
        }
        PersonalTemplate created = personalTemplateService.create(currentUser().getKey(), toInput(dto));
        return Response.status(Response.Status.CREATED).entity(toDto(created)).build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") int id, PersonalTemplateDto dto) {
        Response denied = requireOwner(id);
        if (denied != null) {
            return denied;
        }
        Response invalid = validateTitle(dto);
        if (invalid != null) {
            return invalid;
        }
        PersonalTemplate updated = personalTemplateService.update(id, toInput(dto));
        return Response.ok(toDto(updated)).build();
    }

    /** Template과 동일한 이유(AO @NotNull String은 빈 문자열도 거부하며 500을 던짐) — 미리 걸러줌. */
    private Response validateTitle(PersonalTemplateDto dto) {
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
    @Path("/{id}")
    public Response delete(@PathParam("id") int id) {
        Response denied = requireOwner(id);
        if (denied != null) {
            return denied;
        }
        personalTemplateService.delete(id);
        return Response.noContent().build();
    }

    @PUT
    @Path("/order")
    public Response reorder(List<Integer> orderedIds) {
        Response denied = requireLoggedIn();
        if (denied != null) {
            return denied;
        }
        personalTemplateService.reorder(currentUser().getKey(), orderedIds);
        return Response.noContent().build();
    }

    private Response requireLoggedIn() {
        return currentUser() == null ? Response.status(Response.Status.UNAUTHORIZED).build() : null;
    }

    private Response requireOwner(int personalTemplateId) {
        Response denied = requireLoggedIn();
        if (denied != null) {
            return denied;
        }
        PersonalTemplate template = personalTemplateService.findById(personalTemplateId);
        if (!template.getUserKey().equals(currentUser().getKey())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return null;
    }

    private ApplicationUser currentUser() {
        return authenticationContext.getLoggedInUser();
    }

    private PersonalTemplateDto toDto(PersonalTemplate template) {
        PersonalTemplateDto dto = new PersonalTemplateDto();
        dto.id = template.getID();
        dto.title = template.getTitle();
        dto.color = template.getColor();
        dto.text = template.getText();
        dto.visible = Boolean.TRUE.equals(template.getVisible());
        dto.isAllProjects = Boolean.TRUE.equals(template.getIsAllProjects());
        dto.isAllIssueTypes = Boolean.TRUE.equals(template.getIsAllIssueTypes());
        dto.sortOrder = template.getSortOrder();
        dto.projectKeys = Arrays.stream(template.getProjects())
                .map(PersonalTemplateProject::getProjectKey).collect(Collectors.toSet());
        dto.issueTypeIds = Arrays.stream(template.getIssueTypes())
                .map(PersonalTemplateIssueType::getIssueTypeId).collect(Collectors.toSet());
        return dto;
    }

    private PersonalTemplateService.PersonalTemplateInput toInput(PersonalTemplateDto dto) {
        PersonalTemplateService.PersonalTemplateInput input = new PersonalTemplateService.PersonalTemplateInput();
        input.title = dto.title;
        input.color = dto.color;
        input.text = dto.text;
        input.visible = dto.visible;
        input.isAllProjects = dto.isAllProjects;
        input.isAllIssueTypes = dto.isAllIssueTypes;
        input.projectKeys = dto.projectKeys == null ? Collections.<String>emptySet() : new LinkedHashSet<>(dto.projectKeys);
        input.issueTypeIds = dto.issueTypeIds == null ? Collections.<String>emptySet() : new LinkedHashSet<>(dto.issueTypeIds);
        return input;
    }
}
