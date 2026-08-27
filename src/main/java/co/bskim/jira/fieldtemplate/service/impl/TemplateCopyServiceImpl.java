package co.bskim.jira.fieldtemplate.service.impl;

import co.bskim.jira.fieldtemplate.model.Template;
import co.bskim.jira.fieldtemplate.model.TemplateGroup;
import co.bskim.jira.fieldtemplate.model.TemplateIssueType;
import co.bskim.jira.fieldtemplate.model.TemplateRestriction;
import co.bskim.jira.fieldtemplate.model.TemplateRole;
import co.bskim.jira.fieldtemplate.service.TemplateCopyService;
import co.bskim.jira.fieldtemplate.service.TemplateService;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.DBParam;
import net.java.ao.Query;
import javax.inject.Inject;
import com.atlassian.plugin.spring.scanner.annotation.component.JiraComponent;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@JiraComponent
@ExportAsService(TemplateCopyService.class)
public class TemplateCopyServiceImpl implements TemplateCopyService {

    private final ActiveObjects ao;
    private final TemplateService templateService;

    @Inject
    public TemplateCopyServiceImpl(@ComponentImport ActiveObjects ao, TemplateService templateService) {
        this.ao = ao;
        this.templateService = templateService;
    }

    @Override
    public List<String> candidateSourceProjects(List<String> candidateProjectKeys, String fieldId) {
        return candidateProjectKeys.stream()
                .filter(key -> !templateService.findByProjectAndField(key, fieldId).isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public CopyPreview preview(String sourceProjectKey, String targetProjectKey, String fieldId) {
        List<TemplateSummary> summaries = templateService.findByProjectAndField(sourceProjectKey, fieldId).stream()
                .map(t -> new TemplateSummary(t.getTitle(), t.getColor(), t.getText()))
                .collect(Collectors.toList());
        boolean conflict = !templateService.findByProjectAndField(targetProjectKey, fieldId).isEmpty();
        return new CopyPreview(summaries, conflict);
    }

    @Override
    public void copy(String sourceProjectKey, String targetProjectKey, String fieldId, boolean overwrite) {
        List<Template> targetExisting = templateService.findByProjectAndField(targetProjectKey, fieldId);
        if (!targetExisting.isEmpty()) {
            if (!overwrite) {
                return;
            }
            for (Template existing : targetExisting) {
                templateService.delete(existing.getID());
            }
        }

        for (Template source : templateService.findByProjectAndField(sourceProjectKey, fieldId)) {
            templateService.create(toInput(source, targetProjectKey));
        }
    }

    private TemplateService.TemplateInput toInput(Template source, String targetProjectKey) {
        TemplateService.TemplateInput input = new TemplateService.TemplateInput();
        input.projectKey = targetProjectKey;
        input.fieldId = source.getFieldId();
        input.title = source.getTitle();
        input.color = source.getColor();
        input.text = source.getText();
        input.visible = Boolean.TRUE.equals(source.getVisible());
        input.isDefault = Boolean.TRUE.equals(source.getIsDefault());
        input.screenTypes = csvToSet(source.getScreenTypes());

        input.issueTypeIds = Arrays.stream(source.getIssueTypes())
                .map(TemplateIssueType::getIssueTypeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        input.roleIds = Arrays.stream(source.getRoles())
                .map(TemplateRole::getRoleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        input.restrictions = Arrays.stream(source.getRestrictions())
                .map(r -> new TemplateService.RestrictionInput(r.getType(), r.getTargetKey()))
                .collect(Collectors.toList());

        if (source.getGroup() != null) {
            input.groupId = findOrCreateGroup(targetProjectKey, source.getGroup().getName()).getID();
        }
        return input;
    }

    private TemplateGroup findOrCreateGroup(String projectKey, String groupName) {
        TemplateGroup[] existing = ao.find(TemplateGroup.class, Query.select()
                .where("PROJECT_KEY = ? AND NAME = ?", projectKey, groupName));
        if (existing.length > 0) {
            return existing[0];
        }
        TemplateGroup group = ao.create(TemplateGroup.class,
                new DBParam("PROJECT_KEY", projectKey),
                new DBParam("NAME", groupName));
        return group;
    }

    private Set<String> csvToSet(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return null;
        }
        return new LinkedHashSet<>(Arrays.asList(csv.split(",")));
    }
}
