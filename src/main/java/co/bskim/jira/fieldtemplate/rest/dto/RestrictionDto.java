package co.bskim.jira.fieldtemplate.rest.dto;

import co.bskim.jira.fieldtemplate.util.RestrictionType;

public class RestrictionDto {
    public RestrictionType type;
    public String targetKey;

    public RestrictionDto() {
    }

    public RestrictionDto(RestrictionType type, String targetKey) {
        this.type = type;
        this.targetKey = targetKey;
    }
}
