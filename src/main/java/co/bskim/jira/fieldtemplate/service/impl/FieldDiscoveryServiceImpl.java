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

    /**
     * 커스텀 텍스트 필드도 템플릿 대상으로 노출할지 여부.
     *
     * 현재 false — 삽입 버튼의 위치 계산(wiki 툴바에 붙일 때의 델타 보정, 일반 입력 필드 우측 배치)을
     * 시스템 필드 3개(Summary/Description/Environment) 화면에서만 실기로 맞춰놨기 때문에, 아직 검증하지
     * 않은 커스텀 필드까지 목록에 내보내면 버튼이 엉뚱한 위치에 붙을 수 있다. 사용자 요청이 들어오면
     * 그때 각 커스텀 필드 타입에서 버튼 위치를 확인한 뒤 이 값만 true로 바꾸면 된다 — 아래 탐색
     * 로직은 그대로 살려둔다.
     *
     * 주의: 이 값을 false로 둔 동안에도 이미 커스텀 필드로 만들어둔 템플릿 데이터는 DB에 그대로
     * 남아 있고 지워지지 않는다. 다만 관리 화면의 Select Field 목록과 이슈 화면의 삽입 버튼 양쪽에서
     * 안 보이게 되므로, 그 템플릿들은 다시 true로 바꿀 때까지 사실상 비활성 상태가 된다.
     */
    private static final boolean INCLUDE_CUSTOM_FIELDS = false;

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

        if (!INCLUDE_CUSTOM_FIELDS) {
            return fields;
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
