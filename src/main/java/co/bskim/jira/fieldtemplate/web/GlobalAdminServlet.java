package co.bskim.jira.fieldtemplate.web;

import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
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

/** 전역 관리자 화면 셸 — 인스턴스 전체 템플릿 개요. js/global-admin.js가 REST로 처리. */
@JiraComponent
public class GlobalAdminServlet extends HttpServlet {

    private final GlobalPermissionManager globalPermissionManager;
    private final JiraAuthenticationContext authenticationContext;

    @Inject
    public GlobalAdminServlet(@ComponentImport GlobalPermissionManager globalPermissionManager,
                               @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.globalPermissionManager = globalPermissionManager;
        this.authenticationContext = authenticationContext;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (!globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, user)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        resp.setContentType("text/html;charset=UTF-8");
        PageShell.render(req, resp, "atl.admin", "Field Templates - Global Admin",
                "global-admin.js", "co-bskim-field-templates-global-admin", "");
    }
}
