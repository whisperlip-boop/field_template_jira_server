package co.bskim.jira.fieldtemplate.model;

import net.java.ao.Entity;
import net.java.ao.OneToMany;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.StringLength;

public interface Template extends Entity {

    Integer getSortOrder();
    void setSortOrder(Integer sortOrder);

    @NotNull
    String getTitle();
    void setTitle(String title);

    String getColor();
    void setColor(String color);

    @StringLength(StringLength.UNLIMITED)
    String getText();
    void setText(String text);

    Boolean getVisible();
    void setVisible(Boolean visible);

    Boolean getIsDefault();
    void setIsDefault(Boolean isDefault);

    @NotNull
    String getProjectKey();
    void setProjectKey(String projectKey);

    @NotNull
    String getFieldId();
    void setFieldId(String fieldId);

    Integer getUsageCount();
    void setUsageCount(Integer usageCount);

    /** 콤마로 구분된 화면 종류(CREATE,EDIT,TRANSITION). null/빈 문자열이면 모든 화면에 노출. */
    String getScreenTypes();
    void setScreenTypes(String screenTypes);

    TemplateGroup getGroup();
    void setGroup(TemplateGroup group);

    /** 이 템플릿을 볼 수 있는 대상을 제한하지 않으면 비어 있음(=제한 없음, 프로젝트 전체 공개). */
    @OneToMany
    TemplateIssueType[] getIssueTypes();

    @OneToMany
    TemplateRole[] getRoles();

    @OneToMany
    TemplateRestriction[] getRestrictions();
}
