package co.bskim.jira.fieldtemplate.rest.dto;

import java.util.Set;

public class PersonalTemplateDto {
    public Integer id;
    public String title;
    public String color;
    public String text;
    public boolean visible;
    public boolean isAllProjects;
    public boolean isAllIssueTypes;
    public Integer sortOrder;
    public Set<String> projectKeys;
    public Set<String> issueTypeIds;
}
