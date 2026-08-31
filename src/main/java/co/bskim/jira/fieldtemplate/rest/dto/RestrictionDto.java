package co.bskim.jira.fieldtemplate.rest.dto;

import co.bskim.jira.fieldtemplate.util.RestrictionType;

public class RestrictionDto {
    public RestrictionType type;
    public String targetKey;
    /** USER 타입일 때 화면에 보여줄 "표시 이름 (로그인명)" — targetKey(Jira 내부 사용자 키)는 저장/매칭용으로만 쓰고 사람이 읽을 값은 아니다. */
    public String targetLabel;

    public RestrictionDto() {
    }

    public RestrictionDto(RestrictionType type, String targetKey, String targetLabel) {
        this.type = type;
        this.targetKey = targetKey;
        this.targetLabel = targetLabel;
    }
}
