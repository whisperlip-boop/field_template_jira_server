package co.bskim.jira.fieldtemplate.service.impl;

import co.bskim.jira.fieldtemplate.model.PersonalTemplate;
import co.bskim.jira.fieldtemplate.model.PersonalTemplateIssueType;
import co.bskim.jira.fieldtemplate.model.PersonalTemplateProject;
import co.bskim.jira.fieldtemplate.model.Template;
import co.bskim.jira.fieldtemplate.model.TemplateIssueType;
import co.bskim.jira.fieldtemplate.model.TemplateRestriction;
import co.bskim.jira.fieldtemplate.model.TemplateRole;
import co.bskim.jira.fieldtemplate.service.PersonalTemplateService;
import co.bskim.jira.fieldtemplate.service.TemplateService;
import co.bskim.jira.fieldtemplate.service.TemplateVisibilityService;
import co.bskim.jira.fieldtemplate.util.RestrictionType;
import co.bskim.jira.fieldtemplate.util.ScreenType;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import javax.inject.Inject;
import com.atlassian.plugin.spring.scanner.annotation.component.JiraComponent;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@JiraComponent
@ExportAsService(TemplateVisibilityService.class)
public class TemplateVisibilityServiceImpl implements TemplateVisibilityService {

    private final TemplateService templateService;
    private final PersonalTemplateService personalTemplateService;
    private final ProjectManager projectManager;
    private final ProjectRoleManager projectRoleManager;
    private final GroupManager groupManager;

    @Inject
    public TemplateVisibilityServiceImpl(TemplateService templateService,
                                          PersonalTemplateService personalTemplateService,
                                          @ComponentImport ProjectManager projectManager,
                                          @ComponentImport ProjectRoleManager projectRoleManager,
                                          @ComponentImport GroupManager groupManager) {
        this.templateService = templateService;
        this.personalTemplateService = personalTemplateService;
        this.projectManager = projectManager;
        this.projectRoleManager = projectRoleManager;
        this.groupManager = groupManager;
    }

    @Override
    public List<Template> findVisibleProjectTemplates(ApplicationUser user, String projectKey, String fieldId,
                                                        String issueTypeId, ScreenType screenType) {
        Project project = projectManager.getProjectByCurrentKey(projectKey);
        Set<Long> userRoleIds = project == null ? java.util.Collections.emptySet() : userRoleIds(user, project);

        return templateService.findByProjectAndField(projectKey, fieldId).stream()
                .filter(t -> Boolean.TRUE.equals(t.getVisible()))
                .filter(t -> matchesScreenType(t, screenType))
                .filter(t -> matchesIssueType(t.getIssueTypes(), issueTypeId))
                .filter(t -> matchesRole(t.getRoles(), userRoleIds))
                .filter(t -> matchesRestriction(t.getRestrictions(), user))
                .collect(Collectors.toList());
    }

    @Override
    public List<PersonalTemplate> findVisiblePersonalTemplates(ApplicationUser user, String projectKey, String issueTypeId) {
        return personalTemplateService.findByUser(user.getKey()).stream()
                .filter(t -> Boolean.TRUE.equals(t.getVisible()))
                .filter(t -> t.getIsAllProjects() || containsProjectKey(t.getProjects(), projectKey))
                .filter(t -> t.getIsAllIssueTypes() || containsIssueTypeId(t.getIssueTypes(), issueTypeId))
                .collect(Collectors.toList());
    }

    private boolean matchesScreenType(Template template, ScreenType screenType) {
        String csv = template.getScreenTypes();
        if (csv == null || csv.trim().isEmpty()) {
            return true;
        }
        return Arrays.asList(csv.split(",")).contains(screenType.name());
    }

    private boolean matchesIssueType(TemplateIssueType[] issueTypes, String issueTypeId) {
        if (issueTypes.length == 0) {
            return true;
        }
        return Arrays.stream(issueTypes).anyMatch(it -> it.getIssueTypeId().equals(issueTypeId));
    }

    private boolean matchesRole(TemplateRole[] roles, Set<Long> userRoleIds) {
        if (roles.length == 0) {
            return true;
        }
        return Arrays.stream(roles).anyMatch(r -> userRoleIds.contains(r.getRoleId()));
    }

    private boolean matchesRestriction(TemplateRestriction[] restrictions, ApplicationUser user) {
        if (restrictions.length == 0) {
            return true;
        }
        for (TemplateRestriction restriction : restrictions) {
            if (restriction.getType() == RestrictionType.ANY_LOGGED_IN && user != null) {
                return true;
            }
            if (restriction.getType() == RestrictionType.USER && user != null
                    && user.getKey().equals(restriction.getTargetKey())) {
                return true;
            }
            if (restriction.getType() == RestrictionType.GROUP && user != null
                    && groupManager.isUserInGroup(user, restriction.getTargetKey())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsProjectKey(PersonalTemplateProject[] projects, String projectKey) {
        return Arrays.stream(projects).anyMatch(p -> p.getProjectKey().equals(projectKey));
    }

    private boolean containsIssueTypeId(PersonalTemplateIssueType[] issueTypes, String issueTypeId) {
        return Arrays.stream(issueTypes).anyMatch(it -> it.getIssueTypeId().equals(issueTypeId));
    }

    private Set<Long> userRoleIds(ApplicationUser user, Project project) {
        if (user == null) {
            return java.util.Collections.emptySet();
        }
        Collection<ProjectRole> roles = projectRoleManager.getProjectRoles(user, project);
        return roles.stream().map(ProjectRole::getId).collect(Collectors.toSet());
    }
}
