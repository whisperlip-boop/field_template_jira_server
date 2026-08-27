package co.bskim.jira.fieldtemplate.rest;

import co.bskim.jira.fieldtemplate.model.TemplateGroup;
import co.bskim.jira.fieldtemplate.service.TemplateService;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.Query;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/** 인스턴스 전체 템플릿 개요 — 전역 관리자 화면 전용. */
@Path("/global")
@Produces(MediaType.APPLICATION_JSON)
public class GlobalResource {

    private final TemplateService templateService;
    private final ActiveObjects ao;
    private final ProjectManager projectManager;
    private final GlobalPermissionManager globalPermissionManager;
    private final JiraAuthenticationContext authenticationContext;

    @Inject
    public GlobalResource(TemplateService templateService,
                           @ComponentImport ActiveObjects ao,
                           @ComponentImport ProjectManager projectManager,
                           @ComponentImport GlobalPermissionManager globalPermissionManager,
                           @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.templateService = templateService;
        this.ao = ao;
        this.projectManager = projectManager;
        this.globalPermissionManager = globalPermissionManager;
        this.authenticationContext = authenticationContext;
    }

    @GET
    @Path("/overview")
    public Response overview() {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (!globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, user)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        List<Entry> entries = new ArrayList<>();
        for (Project project : projectManager.getProjectObjects()) {
            int templateCount = templateService.findByProject(project.getKey()).size();
            int groupCount = ao.find(TemplateGroup.class, Query.select().where("PROJECT_KEY = ?", project.getKey())).length;
            if (templateCount > 0 || groupCount > 0) {
                entries.add(new Entry(project.getKey(), project.getName(), templateCount, groupCount));
            }
        }
        return Response.ok(entries).build();
    }

    public static class Entry {
        public String projectKey;
        public String projectName;
        public int templateCount;
        public int groupCount;

        public Entry(String projectKey, String projectName, int templateCount, int groupCount) {
            this.projectKey = projectKey;
            this.projectName = projectName;
            this.templateCount = templateCount;
            this.groupCount = groupCount;
        }
    }
}
