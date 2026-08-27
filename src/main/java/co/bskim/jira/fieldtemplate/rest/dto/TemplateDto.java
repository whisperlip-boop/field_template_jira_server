package co.bskim.jira.fieldtemplate.rest.dto;

import java.util.List;
import java.util.Set;

public class TemplateDto {
    public Integer id;
    public String projectKey;
    public String fieldId;
    public String title;
    public String color;
    public String text;
    public boolean visible;
    public boolean isDefault;
    public Integer sortOrder;
    public Integer usageCount;
    public Integer groupId;
    public Set<String> screenTypes;
    public Set<String> issueTypeIds;
    public Set<Long> roleIds;
    public List<RestrictionDto> restrictions;
}
