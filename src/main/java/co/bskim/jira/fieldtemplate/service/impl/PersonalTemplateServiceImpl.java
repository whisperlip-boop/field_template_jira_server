package co.bskim.jira.fieldtemplate.service.impl;

import co.bskim.jira.fieldtemplate.model.PersonalTemplate;
import co.bskim.jira.fieldtemplate.model.PersonalTemplateIssueType;
import co.bskim.jira.fieldtemplate.model.PersonalTemplateProject;
import co.bskim.jira.fieldtemplate.service.PersonalTemplateService;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.DBParam;
import net.java.ao.Query;
import javax.inject.Inject;
import com.atlassian.plugin.spring.scanner.annotation.component.JiraComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@JiraComponent
@ExportAsService(PersonalTemplateService.class)
public class PersonalTemplateServiceImpl implements PersonalTemplateService {

    private final ActiveObjects ao;

    @Inject
    public PersonalTemplateServiceImpl(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
    }

    @Override
    public PersonalTemplate create(String userKey, PersonalTemplateInput input) {
        // sortOrder는 ao.create()로 행이 즉시 INSERT되기 전에 미리 계산해야 함 (TemplateServiceImpl과 동일 이유).
        int nextOrder = findByUser(userKey).size();
        PersonalTemplate template = ao.create(PersonalTemplate.class,
                new DBParam("TITLE", input.title),
                new DBParam("USER_KEY", userKey));
        applyInput(template, input);
        template.setSortOrder(nextOrder);
        template.save();
        applyRelations(template, input);
        return template;
    }

    @Override
    public PersonalTemplate update(int personalTemplateId, PersonalTemplateInput input) {
        PersonalTemplate template = findById(personalTemplateId);
        applyInput(template, input);
        template.save();
        applyRelations(template, input);
        return template;
    }

    @Override
    public void delete(int personalTemplateId) {
        PersonalTemplate template = findById(personalTemplateId);
        ao.delete(ao.find(PersonalTemplateIssueType.class, Query.select().where("PERSONAL_TEMPLATE_ID = ?", personalTemplateId)));
        ao.delete(ao.find(PersonalTemplateProject.class, Query.select().where("PERSONAL_TEMPLATE_ID = ?", personalTemplateId)));
        ao.delete(template);
    }

    @Override
    public PersonalTemplate findById(int personalTemplateId) {
        PersonalTemplate template = ao.get(PersonalTemplate.class, personalTemplateId);
        if (template == null) {
            throw new IllegalArgumentException("PersonalTemplate not found: " + personalTemplateId);
        }
        return template;
    }

    @Override
    public List<PersonalTemplate> findByUser(String userKey) {
        PersonalTemplate[] templates = ao.find(PersonalTemplate.class, Query.select()
                .where("USER_KEY = ?", userKey)
                .order("SORT_ORDER ASC"));
        return new ArrayList<>(Arrays.asList(templates));
    }

    @Override
    public void reorder(String userKey, List<Integer> orderedPersonalTemplateIds) {
        for (int i = 0; i < orderedPersonalTemplateIds.size(); i++) {
            PersonalTemplate template = findById(orderedPersonalTemplateIds.get(i));
            if (!template.getUserKey().equals(userKey)) {
                throw new IllegalArgumentException("PersonalTemplate " + template.getID() + " does not belong to " + userKey);
            }
            template.setSortOrder(i);
            template.save();
        }
    }

    private void applyInput(PersonalTemplate template, PersonalTemplateInput input) {
        template.setTitle(input.title);
        template.setColor(input.color);
        template.setText(input.text);
        template.setVisible(input.visible);
        template.setIsAllProjects(input.isAllProjects);
        template.setIsAllIssueTypes(input.isAllIssueTypes);
    }

    private void applyRelations(PersonalTemplate template, PersonalTemplateInput input) {
        int templateId = template.getID();

        ao.delete(ao.find(PersonalTemplateProject.class, Query.select().where("PERSONAL_TEMPLATE_ID = ?", templateId)));
        if (!input.isAllProjects && input.projectKeys != null) {
            for (String projectKey : input.projectKeys) {
                ao.create(PersonalTemplateProject.class,
                        new DBParam("PERSONAL_TEMPLATE_ID", template),
                        new DBParam("PROJECT_KEY", projectKey));
            }
        }

        ao.delete(ao.find(PersonalTemplateIssueType.class, Query.select().where("PERSONAL_TEMPLATE_ID = ?", templateId)));
        if (!input.isAllIssueTypes && input.issueTypeIds != null) {
            for (String issueTypeId : input.issueTypeIds) {
                ao.create(PersonalTemplateIssueType.class,
                        new DBParam("PERSONAL_TEMPLATE_ID", template),
                        new DBParam("ISSUE_TYPE_ID", issueTypeId));
            }
        }
    }
}
