package co.bskim.jira.fieldtemplate.service;

import java.util.List;

/** 프로젝트 간, 현재 선택된 필드 하나에 대한 템플릿 설정 복사(충돌 체크 포함). */
public interface TemplateCopyService {

    /** candidateProjectKeys 중 fieldId에 대한 템플릿이 실제로 하나라도 있는 프로젝트 키만 남긴다. */
    List<String> candidateSourceProjects(List<String> candidateProjectKeys, String fieldId);

    /** sourceProjectKey/fieldId에 복사할 템플릿이 몇 개인지, targetProjectKey에 이미 같은 필드 템플릿이 있는지(충돌) 미리보기. */
    CopyPreview preview(String sourceProjectKey, String targetProjectKey, String fieldId);

    /**
     * sourceProjectKey의 fieldId 템플릿들을 targetProjectKey로 복사한다.
     * @param overwrite targetProjectKey에 이미 해당 필드 템플릿이 있을 때(충돌) 덮어쓸지 여부 — false면
     *                  아무 것도 하지 않고 건너뛴다.
     */
    void copy(String sourceProjectKey, String targetProjectKey, String fieldId, boolean overwrite);

    class CopyPreview {
        public final List<TemplateSummary> templates;
        public final boolean conflict;

        public CopyPreview(List<TemplateSummary> templates, boolean conflict) {
            this.templates = templates;
            this.conflict = conflict;
        }
    }

    /** 복사될 템플릿이 실제로 뭔지 미리 보여주기 위한 요약(제목/색/본문) — Preview 화면 전용. */
    class TemplateSummary {
        public final String title;
        public final String color;
        public final String text;

        public TemplateSummary(String title, String color, String text) {
            this.title = title;
            this.color = color;
            this.text = text;
        }
    }
}
