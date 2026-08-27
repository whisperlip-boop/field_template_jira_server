package co.bskim.jira.fieldtemplate.rest;

import co.bskim.jira.fieldtemplate.model.PersonalTemplate;
import co.bskim.jira.fieldtemplate.model.Template;
import co.bskim.jira.fieldtemplate.model.TemplateGroup;
import co.bskim.jira.fieldtemplate.rest.dto.InsertableTemplateDto;
import co.bskim.jira.fieldtemplate.service.ParameterSubstitutionService;
import co.bskim.jira.fieldtemplate.service.TemplateStatisticsService;
import co.bskim.jira.fieldtemplate.service.TemplateVisibilityService;
import co.bskim.jira.fieldtemplate.util.ScreenType;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/** 이슈 화면(Create/Edit/Transition) 삽입 위젯이 호출하는 REST — 노출 가능한 템플릿 조회 + 삽입 시 치환/통계. */
@Path("/insert")
@Produces(MediaType.APPLICATION_JSON)
public class InsertResource {

    private final TemplateVisibilityService visibilityService;
    private final ParameterSubstitutionService substitutionService;
    private final TemplateStatisticsService statisticsService;
    private final IssueManager issueManager;
    private final JiraAuthenticationContext authenticationContext;

    @Inject
    public InsertResource(TemplateVisibilityService visibilityService,
                           ParameterSubstitutionService substitutionService,
                           TemplateStatisticsService statisticsService,
                           @ComponentImport IssueManager issueManager,
                           @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.visibilityService = visibilityService;
        this.substitutionService = substitutionService;
        this.statisticsService = statisticsService;
        this.issueManager = issueManager;
        this.authenticationContext = authenticationContext;
    }

    @GET
    public Response list(@QueryParam("projectKey") String projectKey,
                          @QueryParam("fieldId") String fieldId,
                          @QueryParam("issueTypeId") String issueTypeId,
                          @QueryParam("screenType") ScreenType screenType,
                          @QueryParam("issueKey") String issueKey) {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        Issue issue = issueKey == null || issueKey.isEmpty() ? null : issueManager.getIssueObject(issueKey);

        List<InsertableTemplateDto> results = new ArrayList<>();
        for (Template template : visibilityService.findVisibleProjectTemplates(user, projectKey, fieldId, issueTypeId, screenType)) {
            String text = substitutionService.substitute(template.getText(), issue, user);
            TemplateGroup group = template.getGroup();
            results.add(new InsertableTemplateDto(template.getID(), "PROJECT", template.getTitle(), template.getColor(), text,
                    group != null ? group.getID() : null, group != null ? group.getName() : null,
                    Boolean.TRUE.equals(template.getIsDefault())));
        }
        for (PersonalTemplate template : visibilityService.findVisiblePersonalTemplates(user, projectKey, issueTypeId)) {
            String text = substitutionService.substitute(template.getText(), issue, user);
            results.add(new InsertableTemplateDto(template.getID(), "PERSONAL", template.getTitle(), template.getColor(), text,
                    null, null, false));
        }
        return Response.ok(results).build();
    }

    @POST
    @Path("/{source}/{id}/use")
    public Response recordUse(@PathParam("source") String source, @PathParam("id") int id) {
        if ("PROJECT".equalsIgnoreCase(source)) {
            statisticsService.recordUsage(id);
        }
        return Response.noContent().build();
    }
}
