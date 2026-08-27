package co.bskim.jira.fieldtemplate.web;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.component.JiraComponent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 개인 템플릿 설정 화면 셸 — js/personal-templates.js가 REST로 처리. */
@JiraComponent
public class PersonalTemplatesServlet extends HttpServlet {

    private final JiraAuthenticationContext authenticationContext;

    @Inject
    public PersonalTemplatesServlet(@ComponentImport JiraAuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (user == null) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        resp.setContentType("text/html;charset=UTF-8");
        PageShell.render(req, resp, "atl.general", "My Templates",
                "personal-templates.js", "co-bskim-field-templates-personal-templates", "");
    }
}
