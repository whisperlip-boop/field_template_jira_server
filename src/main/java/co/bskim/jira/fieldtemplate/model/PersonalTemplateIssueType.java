package co.bskim.jira.fieldtemplate.model;

import net.java.ao.Entity;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;

/** 테이블명 명시 필요: 기본 생성명(AO_xxxxxx_PERSONAL_TEMPLATE_ISSUE_TYPE)이 AO의 30자 제한을 초과함. */
@Table("PTMPL_ISSUE_TYPE")
public interface PersonalTemplateIssueType extends Entity {

    PersonalTemplate getPersonalTemplate();
    void setPersonalTemplate(PersonalTemplate personalTemplate);

    @NotNull
    String getIssueTypeId();
    void setIssueTypeId(String issueTypeId);
}
