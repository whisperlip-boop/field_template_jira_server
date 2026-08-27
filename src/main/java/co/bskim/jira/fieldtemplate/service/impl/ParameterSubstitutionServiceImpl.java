package co.bskim.jira.fieldtemplate.service.impl;

import co.bskim.jira.fieldtemplate.service.ParameterSubstitutionService;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.label.Label;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import javax.inject.Inject;
import com.atlassian.plugin.spring.scanner.annotation.component.JiraComponent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@JiraComponent
@ExportAsService(ParameterSubstitutionService.class)
public class ParameterSubstitutionServiceImpl implements ParameterSubstitutionService {

    private static final String EMPTY_PARAMETER = "*None*";
    private static final Pattern CUSTOM_FIELD_TOKEN = Pattern.compile("\\$cf\\[(\\d+)\\]");
    private static final String[] ISSUE_TOKENS = {
            "$reporter", "$nameReporter", "$userNameReporter",
            "$assignee", "$nameAssignee", "$userNameAssignee",
            "$creator", "$description", "$environment", "$summary", "$issuekey",
            "$type", "$status", "$priority", "$resolution",
            "$affectsversions", "$fixversions", "$component", "$labels",
            "$due", "$createdDate", "$createdTime", "$projectKey", "$projectName",
            "$lastcommenter", "$lastcommentbody"
    };

    private final CustomFieldManager customFieldManager;
    private final CommentManager commentManager;

    @Inject
    public ParameterSubstitutionServiceImpl(@ComponentImport CustomFieldManager customFieldManager,
                                             @ComponentImport CommentManager commentManager) {
        this.customFieldManager = customFieldManager;
        this.commentManager = commentManager;
    }

    @Override
    public String substitute(String templateText, Issue issue, ApplicationUser currentUser) {
        if (templateText == null || templateText.isEmpty()) {
            return templateText;
        }

        String result = templateText;
        for (Map.Entry<String, String> entry : buildTokenValues(issue, currentUser).entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        result = substituteCustomFields(result, issue);
        return result;
    }

    /** issue가 null이면(예: Create 다이얼로그처럼 아직 이슈가 존재하지 않는 화면) 이슈 기반 토큰은 전부 EMPTY_PARAMETER. */
    private Map<String, String> buildTokenValues(Issue issue, ApplicationUser currentUser) {
        Map<String, String> tokens = new LinkedHashMap<>();

        tokens.put("$currentUserDisplayName", displayName(currentUser));
        tokens.put("$currentUserName", username(currentUser));
        tokens.put("$currentDate", formatDate(new Date(), "yyyy-MM-dd"));

        if (issue == null) {
            for (String token : ISSUE_TOKENS) {
                tokens.put(token, EMPTY_PARAMETER);
            }
            return tokens;
        }

        ApplicationUser reporter = issue.getReporter();
        tokens.put("$reporter", displayName(reporter));
        tokens.put("$nameReporter", username(reporter));
        tokens.put("$userNameReporter", username(reporter));

        ApplicationUser assignee = issue.getAssignee();
        tokens.put("$assignee", displayName(assignee));
        tokens.put("$nameAssignee", username(assignee));
        tokens.put("$userNameAssignee", username(assignee));

        tokens.put("$creator", displayName(issue.getCreator()));

        tokens.put("$description", orNone(issue.getDescription()));
        tokens.put("$environment", orNone(issue.getEnvironment()));
        tokens.put("$summary", orNone(issue.getSummary()));
        tokens.put("$issuekey", orNone(issue.getKey()));
        tokens.put("$type", issue.getIssueType() == null ? EMPTY_PARAMETER : orNone(issue.getIssueType().getName()));
        tokens.put("$status", issue.getStatusObject() == null ? EMPTY_PARAMETER : orNone(issue.getStatusObject().getName()));
        tokens.put("$priority", issue.getPriorityObject() == null ? EMPTY_PARAMETER : orNone(issue.getPriorityObject().getName()));
        tokens.put("$resolution", issue.getResolutionObject() == null ? EMPTY_PARAMETER : orNone(issue.getResolutionObject().getName()));

        tokens.put("$affectsversions", joinNames(issue.getAffectedVersions(), Version::getName));
        tokens.put("$fixversions", joinNames(issue.getFixVersions(), Version::getName));
        tokens.put("$component", joinNames(issue.getComponentObjects(), ProjectComponent::getName));
        tokens.put("$labels", joinNames(issue.getLabels(), Label::getLabel));

        tokens.put("$due", formatDate(issue.getDueDate(), "yyyy-MM-dd"));
        tokens.put("$createdDate", formatDate(issue.getCreated(), "yyyy-MM-dd"));
        tokens.put("$createdTime", formatDate(issue.getCreated(), "HH:mm:ss"));

        tokens.put("$projectKey", issue.getProjectObject() == null ? EMPTY_PARAMETER : orNone(issue.getProjectObject().getKey()));
        tokens.put("$projectName", issue.getProjectObject() == null ? EMPTY_PARAMETER : orNone(issue.getProjectObject().getName()));

        Comment lastComment = lastComment(issue);
        tokens.put("$lastcommenter", lastComment == null ? EMPTY_PARAMETER : displayName(lastComment.getAuthorApplicationUser()));
        tokens.put("$lastcommentbody", lastComment == null ? EMPTY_PARAMETER : orNone(lastComment.getBody()));

        return tokens;
    }

    private String substituteCustomFields(String text, Issue issue) {
        Matcher matcher = CUSTOM_FIELD_TOKEN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            Long customFieldId = Long.valueOf(matcher.group(1));
            CustomField customField = issue == null ? null : customFieldManager.getCustomFieldObject(customFieldId);
            String value = EMPTY_PARAMETER;
            if (customField != null) {
                Object rawValue = issue.getCustomFieldValue(customField);
                value = formatCustomFieldValue(rawValue);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String formatCustomFieldValue(Object rawValue) {
        if (rawValue == null) {
            return EMPTY_PARAMETER;
        }
        if (rawValue instanceof Iterable) {
            StringBuilder sb = new StringBuilder();
            for (Object item : (Iterable<?>) rawValue) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(item);
            }
            return orNone(sb.toString());
        }
        return orNone(rawValue.toString());
    }

    private Comment lastComment(Issue issue) {
        List<Comment> comments = commentManager.getComments(issue);
        if (comments == null || comments.isEmpty()) {
            return null;
        }
        return comments.get(comments.size() - 1);
    }

    private String displayName(ApplicationUser user) {
        return user == null ? EMPTY_PARAMETER : orNone(user.getDisplayName());
    }

    private String username(ApplicationUser user) {
        return user == null ? EMPTY_PARAMETER : orNone(user.getUsername());
    }

    private String orNone(String value) {
        return value == null || value.isEmpty() ? EMPTY_PARAMETER : value;
    }

    private String formatDate(Date date, String pattern) {
        return date == null ? EMPTY_PARAMETER : new SimpleDateFormat(pattern).format(date);
    }

    private <T> String joinNames(java.util.Collection<T> items, java.util.function.Function<T, String> nameFn) {
        if (items == null || items.isEmpty()) {
            return EMPTY_PARAMETER;
        }
        return items.stream().map(nameFn).collect(Collectors.joining(", "));
    }
}
