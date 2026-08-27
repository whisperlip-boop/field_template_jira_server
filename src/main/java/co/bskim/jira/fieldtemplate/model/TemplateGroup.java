package co.bskim.jira.fieldtemplate.model;

import net.java.ao.Entity;
import net.java.ao.OneToMany;
import net.java.ao.schema.NotNull;

public interface TemplateGroup extends Entity {

    @NotNull
    String getName();
    void setName(String name);

    @NotNull
    String getProjectKey();
    void setProjectKey(String projectKey);

    Integer getSortOrder();
    void setSortOrder(Integer sortOrder);

    @OneToMany
    Template[] getTemplates();
}
