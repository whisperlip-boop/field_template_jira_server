package co.bskim.jira.fieldtemplate.service;

import co.bskim.jira.fieldtemplate.model.PersonalTemplate;

import java.util.List;
import java.util.Set;

/** 사용자별 개인 템플릿 CRUD. */
public interface PersonalTemplateService {

    PersonalTemplate create(String userKey, PersonalTemplateInput input);

    PersonalTemplate update(int personalTemplateId, PersonalTemplateInput input);

    void delete(int personalTemplateId);

    PersonalTemplate findById(int personalTemplateId);

    /** 사용자의 개인 템플릿 전체(정렬 순서대로). */
    List<PersonalTemplate> findByUser(String userKey);

    void reorder(String userKey, List<Integer> orderedPersonalTemplateIds);

    class PersonalTemplateInput {
        public String title;
        public String color;
        public String text;
        public boolean visible = true;
        public boolean isAllProjects = true;
        public boolean isAllIssueTypes = true;
        public Set<String> projectKeys;
        public Set<String> issueTypeIds;
    }
}
