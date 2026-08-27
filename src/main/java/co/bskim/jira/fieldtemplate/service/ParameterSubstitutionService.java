package co.bskim.jira.fieldtemplate.service;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;

/** 템플릿 텍스트 안의 파라미터 치환 토큰($reporter, $cf[123] 등)을 이슈 데이터로 치환. */
public interface ParameterSubstitutionService {

    /**
     * @param currentUser 삽입 버튼을 누른 사용자(=$currentUser* 토큰에 쓰임). 미로그인 컨텍스트는 없으므로 null 아님을 가정.
     */
    String substitute(String templateText, Issue issue, ApplicationUser currentUser);
}
