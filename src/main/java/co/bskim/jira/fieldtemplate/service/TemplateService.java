package co.bskim.jira.fieldtemplate.service;

import co.bskim.jira.fieldtemplate.model.Template;
import co.bskim.jira.fieldtemplate.util.RestrictionType;

import java.util.List;
import java.util.Set;

/** 프로젝트(관리자) 템플릿 CRUD. */
public interface TemplateService {

    Template create(TemplateInput input);

    Template update(int templateId, TemplateInput input);

    void delete(int templateId);

    Template findById(int templateId);

    /** 특정 프로젝트의 특정 필드에 등록된 템플릿을 정렬 순서대로. */
    List<Template> findByProjectAndField(String projectKey, String fieldId);

    /** 프로젝트에 등록된 모든 템플릿(필드 무관, 관리자 설정 화면용). */
    List<Template> findByProject(String projectKey);

    /** orderedTemplateIds 순서대로 sortOrder를 재부여. */
    void reorder(String projectKey, String fieldId, List<Integer> orderedTemplateIds);

    class TemplateInput {
        public String projectKey;
        public String fieldId;
        public String title;
        public String color;
        public String text;
        public boolean visible = true;
        public boolean isDefault = false;
        public Integer groupId;
        public Set<String> screenTypes;
        public Set<String> issueTypeIds;
        public Set<Long> roleIds;
        public List<RestrictionInput> restrictions;
    }

    class RestrictionInput {
        public RestrictionType type;
        public String targetKey;

        public RestrictionInput() {
        }

        public RestrictionInput(RestrictionType type, String targetKey) {
            this.type = type;
            this.targetKey = targetKey;
        }
    }
}
