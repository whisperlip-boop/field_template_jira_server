package co.bskim.jira.fieldtemplate.service;

import co.bskim.jira.fieldtemplate.model.PersonalTemplate;
import co.bskim.jira.fieldtemplate.model.Template;
import co.bskim.jira.fieldtemplate.util.ScreenType;
import com.atlassian.jira.user.ApplicationUser;

import java.util.List;

/** 주어진 (사용자, 프로젝트, 이슈타입, 화면)에서 어떤 템플릿이 보여야 하는지 판정. */
public interface TemplateVisibilityService {

    List<Template> findVisibleProjectTemplates(ApplicationUser user, String projectKey, String fieldId,
                                                String issueTypeId, ScreenType screenType);

    List<PersonalTemplate> findVisiblePersonalTemplates(ApplicationUser user, String projectKey, String issueTypeId);
}
