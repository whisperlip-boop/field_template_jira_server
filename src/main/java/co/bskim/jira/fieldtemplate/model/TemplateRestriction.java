package co.bskim.jira.fieldtemplate.model;

import co.bskim.jira.fieldtemplate.util.RestrictionType;
import net.java.ao.Entity;
import net.java.ao.schema.NotNull;

/**
 * Template을 볼 수 있는 대상을 사용자/그룹/로그인한 모든 사용자 단위로 제한한다.
 * type=USER 이면 targetKey는 사용자 key, type=GROUP 이면 그룹명, type=ANY_LOGGED_IN 이면 targetKey는 사용하지 않는다.
 * 한 템플릿에 이 레코드가 하나도 없으면 "역할(TemplateRole) 조건만으로 판단"을 의미한다.
 */
public interface TemplateRestriction extends Entity {

    Template getTemplate();
    void setTemplate(Template template);

    @NotNull
    RestrictionType getType();
    void setType(RestrictionType type);

    String getTargetKey();
    void setTargetKey(String targetKey);
}
