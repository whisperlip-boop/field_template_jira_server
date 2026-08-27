package co.bskim.jira.fieldtemplate.model;

import net.java.ao.Entity;
import net.java.ao.OneToMany;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.StringLength;

public interface PersonalTemplate extends Entity {

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

    Boolean getIsAllProjects();
    void setIsAllProjects(Boolean isAllProjects);

    Boolean getIsAllIssueTypes();
    void setIsAllIssueTypes(Boolean isAllIssueTypes);

    @NotNull
    String getUserKey();
    void setUserKey(String userKey);

    @OneToMany
    PersonalTemplateIssueType[] getIssueTypes();

    @OneToMany
    PersonalTemplateProject[] getProjects();
}
