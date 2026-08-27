package co.bskim.jira.fieldtemplate.model;

import net.java.ao.Entity;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.Table;

/**
 * PersonalTemplate과 프로젝트 키의 다대다 조인. isAllProjects=false일 때만 사용.
 * 테이블명 명시 필요: 기본 생성명(AO_xxxxxx_PERSONAL_TEMPLATE_PROJECT)이 AO의 30자 제한을 초과함.
 */
@Table("PTMPL_PROJECT")
public interface PersonalTemplateProject extends Entity {

    PersonalTemplate getPersonalTemplate();
    void setPersonalTemplate(PersonalTemplate personalTemplate);

    @NotNull
    String getProjectKey();
    void setProjectKey(String projectKey);
}
