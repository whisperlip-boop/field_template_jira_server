package co.bskim.jira.fieldtemplate.service.impl;

import co.bskim.jira.fieldtemplate.model.Template;
import co.bskim.jira.fieldtemplate.model.TemplateIssueType;
import co.bskim.jira.fieldtemplate.model.TemplateRestriction;
import co.bskim.jira.fieldtemplate.model.TemplateRole;
import co.bskim.jira.fieldtemplate.model.TemplateUsageStat;
import co.bskim.jira.fieldtemplate.service.TemplateService;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.DBParam;
import net.java.ao.Query;
import javax.inject.Inject;
import com.atlassian.plugin.spring.scanner.annotation.component.JiraComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@JiraComponent
@ExportAsService(TemplateService.class)
public class TemplateServiceImpl implements TemplateService {

    private final ActiveObjects ao;

    @Inject
    public TemplateServiceImpl(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
    }

    @Override
    public Template create(TemplateInput input) {
        // sortOrder는 ao.create()가 행을 즉시 INSERT하기 전에 미리 계산해야 함 — 아니면
        // findByProjectAndField()가 방금 만든 행 자신까지 세어 순서가 하나 밀린다.
        int nextOrder = findByProjectAndField(input.projectKey, input.fieldId).size();

        // Template.title/projectKey/fieldId는 @NotNull이라 ao.create() 호출 시점에 넘겨야 함
        // (ao.create()는 즉시 INSERT를 실행하므로 이후 setter+save()로는 NOT NULL 제약을 못 맞춤).
        Template template = ao.create(Template.class,
                new DBParam("TITLE", input.title),
                new DBParam("PROJECT_KEY", input.projectKey),
                new DBParam("FIELD_ID", input.fieldId));
        applyInput(template, input);
        template.setSortOrder(nextOrder);
        template.save();
        applyRelations(template, input);
        return template;
    }

    @Override
    public Template update(int templateId, TemplateInput input) {
        Template template = findById(templateId);
        applyInput(template, input);
        template.save();
        applyRelations(template, input);
        return template;
    }

    @Override
    public void delete(int templateId) {
        Template template = findById(templateId);
        ao.delete(ao.find(TemplateIssueType.class, Query.select().where("TEMPLATE_ID = ?", templateId)));
        ao.delete(ao.find(TemplateRole.class, Query.select().where("TEMPLATE_ID = ?", templateId)));
        ao.delete(ao.find(TemplateRestriction.class, Query.select().where("TEMPLATE_ID = ?", templateId)));
        ao.delete(ao.find(TemplateUsageStat.class, Query.select().where("TEMPLATE_ID = ?", templateId)));
        ao.delete(template);
    }

    @Override
    public Template findById(int templateId) {
        Template template = ao.get(Template.class, templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }
        return template;
    }

    @Override
    public List<Template> findByProjectAndField(String projectKey, String fieldId) {
        Template[] templates = ao.find(Template.class, Query.select()
                .where("PROJECT_KEY = ? AND FIELD_ID = ?", projectKey, fieldId)
                .order("SORT_ORDER ASC"));
        return new ArrayList<>(java.util.Arrays.asList(templates));
    }

    @Override
    public List<Template> findByProject(String projectKey) {
        Template[] templates = ao.find(Template.class, Query.select()
                .where("PROJECT_KEY = ?", projectKey)
                .order("FIELD_ID ASC, SORT_ORDER ASC"));
        return new ArrayList<>(java.util.Arrays.asList(templates));
    }

    @Override
    public void reorder(String projectKey, String fieldId, List<Integer> orderedTemplateIds) {
        for (int i = 0; i < orderedTemplateIds.size(); i++) {
            Template template = findById(orderedTemplateIds.get(i));
            if (!template.getProjectKey().equals(projectKey) || !template.getFieldId().equals(fieldId)) {
                throw new IllegalArgumentException("Template " + template.getID() + " does not belong to " + projectKey + "/" + fieldId);
            }
            template.setSortOrder(i);
            template.save();
        }
    }

    private void applyInput(Template template, TemplateInput input) {
        template.setProjectKey(input.projectKey);
        template.setFieldId(input.fieldId);
        template.setTitle(input.title);
        template.setColor(input.color);
        template.setText(input.text);
        template.setVisible(input.visible);
        template.setIsDefault(input.isDefault);
        template.setScreenTypes(joinCsv(input.screenTypes));
        if (input.groupId != null) {
            template.setGroup(ao.get(co.bskim.jira.fieldtemplate.model.TemplateGroup.class, input.groupId));
        } else {
            template.setGroup(null);
        }
    }

    private void applyRelations(Template template, TemplateInput input) {
        int templateId = template.getID();

        ao.delete(ao.find(TemplateIssueType.class, Query.select().where("TEMPLATE_ID = ?", templateId)));
        if (input.issueTypeIds != null) {
            for (String issueTypeId : input.issueTypeIds) {
                ao.create(TemplateIssueType.class,
                        new DBParam("TEMPLATE_ID", template),
                        new DBParam("ISSUE_TYPE_ID", issueTypeId));
            }
        }

        ao.delete(ao.find(TemplateRole.class, Query.select().where("TEMPLATE_ID = ?", templateId)));
        if (input.roleIds != null) {
            for (Long roleId : input.roleIds) {
                ao.create(TemplateRole.class,
                        new DBParam("TEMPLATE_ID", template),
                        new DBParam("ROLE_ID", roleId));
            }
        }

        ao.delete(ao.find(TemplateRestriction.class, Query.select().where("TEMPLATE_ID = ?", templateId)));
        if (input.restrictions != null) {
            for (RestrictionInput restriction : input.restrictions) {
                ao.create(TemplateRestriction.class,
                        new DBParam("TEMPLATE_ID", template),
                        new DBParam("TYPE", restriction.type),
                        new DBParam("TARGET_KEY", restriction.targetKey));
            }
        }
    }

    private String joinCsv(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return String.join(",", sorted);
    }
}
