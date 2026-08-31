package co.bskim.jira.fieldtemplate.service;

import co.bskim.jira.fieldtemplate.model.TemplateUsageStat;

import java.util.List;

/** 필드별/템플릿별 사용 통계 적재·조회. */
public interface TemplateStatisticsService {

    /** 템플릿이 실제로 필드에 삽입될 때 호출 — Template.usageCount와 TemplateUsageStat을 함께 갱신. */
    void recordUsage(int templateId);

    /** 특정 프로젝트의 특정 필드에서 가장 많이 쓰인 템플릿 순. 필드는 여러 프로젝트에서 공유될 수 있으므로 projectKey로 범위를 좁힌다. */
    List<TemplateUsageStat> findTopByField(String projectKey, String fieldId, int limit);
}
