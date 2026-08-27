package co.bskim.jira.fieldtemplate.service.impl;

import co.bskim.jira.fieldtemplate.service.FieldDiscoveryService;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import javax.inject.Inject;
import com.atlassian.plugin.spring.scanner.annotation.component.JiraComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@JiraComponent
@ExportAsService(FieldDiscoveryService.class)
public class FieldDiscoveryServiceImpl implements FieldDiscoveryService {

    /** 원본 플러그인의 파라미터 치환 토큰 표에 대응하는 시스템 텍스트 필드. */
    private static final String[][] SYSTEM_TEXT_FIELDS = {
            {"summary", "Summary"},
            {"description", "Description"},
            {"environment", "Environment"}
    };

    private static final Set<String> TEXT_CUSTOM_FIELD_TYPE_KEYS = new java.util.HashSet<>(Arrays.asList(
            "com.atlassian.jira.plugin.system.customfieldtypes:textfield",
            "com.atlassian.jira.plugin.system.customfieldtypes:textarea"
    ));

    private final CustomFieldManager customFieldManager;
    private final ProjectManager projectManager;

    @Inject
    public FieldDiscoveryServiceImpl(@ComponentImport CustomFieldManager customFieldManager,
                                      @ComponentImport ProjectManager projectManager) {
        this.customFieldManager = customFieldManager;
        this.projectManager = projectManager;
    }

    @Override
    public List<FieldDescriptor> findTextFields(String projectKey, String issueTypeId) {
        List<FieldDescriptor> fields = new ArrayList<>();
        for (String[] systemField : SYSTEM_TEXT_FIELDS) {
            fields.add(new FieldDescriptor(systemField[0], systemField[1], false));
        }

        Project project = projectManager.getProjectByCurrentKey(projectKey);
        if (project != null) {
            List<CustomField> customFields = customFieldManager.getCustomFieldObjects(project.getId(), issueTypeId);
            for (CustomField customField : customFields) {
                if (TEXT_CUSTOM_FIELD_TYPE_KEYS.contains(customField.getCustomFieldType().getKey())) {
                    fields.add(new FieldDescriptor(customField.getId(), customField.getName(), true));
                }
            }
        }
        return fields;
    }
}
