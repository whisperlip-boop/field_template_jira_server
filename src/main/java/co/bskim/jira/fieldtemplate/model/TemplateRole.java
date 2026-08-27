package co.bskim.jira.fieldtemplate.model;

import net.java.ao.Entity;
import net.java.ao.schema.NotNull;

/** Template과 프로젝트 역할(Project Role) ID의 다대다 조인. 비어 있으면 "모든 역할"을 의미. */
public interface TemplateRole extends Entity {

    Template getTemplate();
    void setTemplate(Template template);

    @NotNull
    Long getRoleId();
    void setRoleId(Long roleId);
}
