package co.bskim.jira.fieldtemplate.web;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.component.JiraComponent;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugin.webresource.WebResourceManager;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 프로젝트 관리자 설정 화면 셸(mount point) — 실제 UI/로직은 js/project-config.js가 REST로 처리. */
@JiraComponent
public class ProjectConfigServlet extends HttpServlet {

    private final ProjectManager projectManager;
    private final PermissionManager permissionManager;
    private final JiraAuthenticationContext authenticationContext;
    private final WebResourceManager webResourceManager;

    @Inject
    public ProjectConfigServlet(@ComponentImport ProjectManager projectManager,
                                 @ComponentImport PermissionManager permissionManager,
                                 @ComponentImport JiraAuthenticationContext authenticationContext,
                                 @ComponentImport WebResourceManager webResourceManager) {
        this.projectManager = projectManager;
        this.permissionManager = permissionManager;
        this.authenticationContext = authenticationContext;
        this.webResourceManager = webResourceManager;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String projectKey = req.getParameter("projectKey");
        Project project = projectKey == null ? null : projectManager.getProjectByCurrentKey(projectKey);
        ApplicationUser user = authenticationContext.getLoggedInUser();

        if (project == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown project: " + projectKey);
            return;
        }
        if (!permissionManager.hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, project, user)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // 버그 #35: jira-projects-plugin 모듈을 하나씩 골라 의존성 선언하는 대신, 실제 네이티브
        // project-config 페이지가 쓰는 것과 동일한 WRM 컨텍스트를 그대로 요청한다(사용자가 보내준
        // 실 서버 페이지 소스에서 확인) — 이러면 Jira가 그 사이드바 렌더링에 실제로 필요하다고 정의한
        // CSS/아이콘이 전부 자동으로 딸려온다(Board 아이콘처럼 우리가 미처 몰랐던 것까지 포함).
        // atl.general 전역 컨텍스트에는 안 물려 있으므로(버그 #32) 이 요청 안에서만 유효 — 다른
        // 페이지(대시보드 등)로는 여전히 전혀 안 새어나간다. js 파일들은 PageShell이 직접 <script
        // src>로 받아오므로 이 호출과 무관하게 항상 동작한다.
        webResourceManager.requireResourcesForContext("jira.project.sidebar");
        webResourceManager.requireResourcesForContext("com.atlassian.jira.projects.sidebar.init");

        resp.setContentType("text/html;charset=UTF-8");
        String contextPath = req.getContextPath();
        String key = project.getKey();
        String id = project.getId().toString();
        String projectConfigBase = contextPath + "/plugins/servlet/project-config/" + key + "/";

        // 최좌측 사이드바: 손으로 근사(아이콘 클래스, 폭, 하단 고정 등)하다가 계속 세부적으로
        // 어긋나서(사용자와 여러 라운드 실기 확인), Jira의 진짜 project-config 페이지를 서버에서
        // 내부적으로 한 번 더 요청해 그 안의 BigPipe 사이드바 HTML(WRM._unparsedData["sidebar-id"])
        // 을 그대로 뽑아 쓰는 방식으로 전환(NativeSidebarFetcher). 실패 시(네트워크 오류, 마크업
        // 변경 등)엔 기존 손수 작성한 백업 마크업으로 넘어간다 — 어느 쪽이든 id="ft-back-panel"을
        // 붙이고 접기/펴기는 아래 공용 스크립트가 처리한다.
        String nativeSidebar = NativeSidebarFetcher.fetch(req, key);
        String sidebarHtml = nativeSidebar != null
                ? nativeSidebar.replaceFirst("<section ", "<section id=\"ft-back-panel\" ")
                : buildFallbackSidebar(req, project);
        String sidebarToggleScript = "<script>(function(){"
                + "var b=document.querySelector('#ft-back-panel .aui-sidebar-toggle');"
                + "if(b){b.addEventListener('click',function(){"
                + "document.getElementById('ft-back-panel').classList.toggle('ft-collapsed');});}"
                + "})();</script>";
        String backPanel = "<div class=\"ft-project-shell\">" + sidebarHtml + sidebarToggleScript
                + "<div class=\"ft-project-main-column\">";

        String header = "<section aria-label=\"Page\"><div class=\"aui-page-header\"><div class=\"aui-page-header-inner\">"
                + "<div id=\"project-config-header\"><div class=\"aui-page-header-main\">"
                + "<h1 id=\"project-config-header-name\">Project settings</h1></div></div></div></div>"
                + "<div class=\"aui-page-panel\"><div class=\"aui-page-panel-inner\">"
                + "<div class=\"aui-page-panel-nav\"><nav class=\"aui-navgroup aui-navgroup-vertical\" aria-label=\"Main\">"
                + "<div class=\"aui-navgroup-inner\"><div class=\"aui-navgroup-primary\"><div class=\"admin-menu-links\">"
                + "<ul class=\"aui-nav\">"
                + navItem(projectConfigBase + "summary", "Summary", false)
                + navItem(contextPath + "/secure/project/EditProject!default.jspa?pid=" + id, "Details", false)
                + navItem(contextPath + "/secure/admin/IndexProject.jspa?key=" + key, "Re-index project", false)
                + navItem(contextPath + "/secure/project/DeleteProject!default.jspa?pid=" + id + "&pcp=true", "Delete project", false)
                + "</ul>"
                + "<ul class=\"aui-nav\">"
                + navItem(projectConfigBase + "workflows", "Workflows", false)
                + navItem(projectConfigBase + "screens", "Screens", false)
                + navItem(projectConfigBase + "fields", "Fields", false)
                + navItem(projectConfigBase + "priorities", "Priorities", false)
                + "</ul>"
                + "<ul class=\"aui-nav\">"
                + navItem(projectConfigBase + "administer-versions", "Versions", false)
                + navItem(projectConfigBase + "administer-components", "Components", false)
                + "</ul>"
                + "<ul class=\"aui-nav\">"
                + navItem(projectConfigBase + "roles", "Users and roles", false)
                + navItem(projectConfigBase + "permissions", "Permissions", false)
                + navItem(projectConfigBase + "issuesecurity", "Issue Security", false)
                + navItem(projectConfigBase + "notifications", "Notifications", false)
                + "</ul>"
                + "<ul class=\"aui-nav\">"
                + navItem(contextPath + "/plugins/servlet/field-templates/project-config?projectKey=" + key, "Field Templates", true)
                + "</ul>"
                + "</div></div></div></nav></div>"
                + "<main role=\"main\" id=\"main\" class=\"aui-page-panel-content\">";
        String footer = "</main></div></div></section></div></div>";

        // atl.admin은 시스템 Administration 화면 크롬(Applications/Manage apps/...)을 붙이므로
        // 프로젝트 관리자 전용 화면엔 부적절 — atl.general로 바꾸고 위 header/사이드바로 실제
        // Project settings 화면과 같은 구조를 직접 그려준다.
        PageShell.render(req, resp, "atl.general", "Field Templates - " + project.getName(),
                "project-config.js", "co-bskim-field-templates-project-config",
                "window.FieldTemplatesContext = { projectKey: " + PageShell.jsonString(projectKey) + " };",
                backPanel + header, footer);
    }

    /** NativeSidebarFetcher가 실패했을 때만 쓰는 손수 작성한 근사 사이드바. */
    private static String buildFallbackSidebar(HttpServletRequest req, Project project) {
        String contextPath = req.getContextPath();
        String key = project.getKey();
        String id = project.getId().toString();
        Long avatarId = project.getAvatar() != null ? project.getAvatar().getId() : null;
        String avatarUrl = contextPath + "/secure/projectavatar?" + (avatarId != null ? "avatarId=" + avatarId : "pid=" + id);
        String projectName = escapeHtml(project.getName());
        return "<section class=\"aui-sidebar projects-sidebar\" id=\"ft-back-panel\">"
                + "<div class=\"aui-sidebar-wrapper\"><div class=\"aui-sidebar-body\">"
                + "<div class=\"aui-page-header\"><div class=\"aui-page-header-inner\">"
                + "<div class=\"aui-page-header-image\"><a href=\"" + contextPath + "/projects/" + key + "/summary\" title=\"" + projectName + "\" class=\"jira-project-avatar\">"
                + "<span class=\"aui-avatar aui-avatar-large aui-avatar-project\"><span class=\"aui-avatar-inner\">"
                + "<img src=\"" + avatarUrl + "\" alt=\"" + projectName + "\"></span></span></a></div>"
                + "<div class=\"aui-page-header-main\"><h1><div class=\"aui-group aui-group-split\"><div class=\"aui-item project-title\">"
                + "<a href=\"" + contextPath + "/projects/" + key + "/summary\" title=\"" + projectName + "\">" + projectName + "</a>"
                + "</div></div></h1></div>"
                + "</div></div>"
                + "<nav class=\"aui-navgroup aui-navgroup-vertical\"><div class=\"aui-navgroup-inner sidebar-content-container jira-navigation\">"
                + "<div class=\"aui-sidebar-group aui-sidebar-group-tier-one\"><ul class=\"aui-nav\">"
                + sidebarNavItem(contextPath + "/projects/" + key + "/issues", "icon-sidebar-issues aui-iconfont-issues", "Issues")
                + sidebarNavItem(contextPath + "/projects/" + key + "?selectedItem=com.atlassian.jira.jira-projects-plugin:report-page", "agile-icon-report aui-iconfont-graph-line", "Reports")
                + sidebarNavItem(contextPath + "/projects/" + key + "?selectedItem=com.atlassian.jira.jira-projects-plugin:release-page", "icon-sidebar-release aui-iconfont-ship", "Releases")
                + sidebarNavItem(contextPath + "/projects/" + key + "?selectedItem=com.atlassian.jira.jira-projects-plugin:components-page", "icon-sidebar-components", "Components")
                + "</ul></div>"
                + "</div></nav>"
                + "</div>"
                + "<div class=\"aui-sidebar-footer\">"
                + "<a href=\"" + contextPath + "/plugins/servlet/project-config/" + key + "\" class=\"aui-button aui-button-subtle aui-sidebar-settings-button\">"
                + "<span class=\"aui-icon aui-icon-small aui-iconfont-configure\"></span><span class=\"aui-button-label\">Project settings</span></a>"
                + "<button type=\"button\" class=\"aui-button aui-button-subtle aui-sidebar-toggle aui-sidebar-footer-tipsy\" title=\"Expand/collapse\">"
                + "<span class=\"aui-icon aui-icon-small aui-iconfont-chevron-double-left\"></span></button>"
                + "</div>"
                + "</div></section>";
    }

    private static String navItem(String href, String label, boolean selected) {
        return "<li" + (selected ? " class=\"aui-nav-selected\"" : "") + "><a href=\"" + href + "\">"
                + escapeHtml(label) + "</a></li>";
    }

    private static String sidebarNavItem(String href, String iconClass, String label) {
        return "<li><a class=\"aui-nav-item\" href=\"" + href + "\">"
                + "<span class=\"aui-icon aui-icon-large " + iconClass + "\"></span>"
                + "<span class=\"aui-nav-item-label\">" + escapeHtml(label) + "</span></a></li>";
    }

    private static String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
