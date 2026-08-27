package co.bskim.jira.fieldtemplate.rest.dto;

public class InsertableTemplateDto {
    public int id;
    public String source; // PROJECT | PERSONAL
    public String title;
    public String color;
    public String text;
    public Integer groupId;
    public String groupName;
    public boolean isDefault;

    public InsertableTemplateDto() {
    }

    public InsertableTemplateDto(int id, String source, String title, String color, String text,
                                  Integer groupId, String groupName, boolean isDefault) {
        this.id = id;
        this.source = source;
        this.title = title;
        this.color = color;
        this.text = text;
        this.groupId = groupId;
        this.groupName = groupName;
        this.isDefault = isDefault;
    }
}
