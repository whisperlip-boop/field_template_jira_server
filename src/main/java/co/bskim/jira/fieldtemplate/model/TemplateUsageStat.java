package co.bskim.jira.fieldtemplate.model;

import net.java.ao.Entity;
import net.java.ao.schema.NotNull;

/** 필드(fieldId)에 템플릿(Template)이 삽입된 횟수를 집계. */
public interface TemplateUsageStat extends Entity {

    Template getTemplate();
    void setTemplate(Template template);

    @NotNull
    String getFieldId();
    void setFieldId(String fieldId);

    Integer getCount();
    void setCount(Integer count);
}
