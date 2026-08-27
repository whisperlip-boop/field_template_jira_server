package co.bskim.jira.fieldtemplate.model;

import net.java.ao.Entity;
import net.java.ao.schema.NotNull;

/** Template과 이슈타입 ID의 다대다 조인. 비어 있으면 "모든 이슈타입"을 의미. */
public interface TemplateIssueType extends Entity {

    Template getTemplate();
    void setTemplate(Template template);

    @NotNull
    String getIssueTypeId();
    void setIssueTypeId(String issueTypeId);
}
