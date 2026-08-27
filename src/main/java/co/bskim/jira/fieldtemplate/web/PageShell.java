package co.bskim.jira.fieldtemplate.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * 세 관리 화면(프로젝트/개인/전역)이 공유하는 얇은 HTML 셸. 실제 UI는 클라이언트 JS(REST 호출)가
 * 그린다 — 서버는 Sitemesh 데코레이터 메타태그 + 마운트 div + 컨텍스트 변수만 내려준다.
 */
final class PageShell {

    private static final String PLUGIN_RESOURCE_BASE =
            "/download/resources/co.bskim.jira.field-templates:field-templates-resources/";

    private PageShell() {
    }

    static void render(HttpServletRequest req, HttpServletResponse resp, String decorator, String title,
                        String jsFileName, String mountDivId, String contextScript) throws IOException {
        render(req, resp, decorator, title, jsFileName, mountDivId, contextScript, null, null);
    }

    /**
     * beforeMountHtml/afterMountHtml이 있으면 마운트 div 바깥쪽을 감싸는 형태로 그대로 출력한다 —
     * atl.general 데코레이터는 프로젝트 사이드바 없이 일반 Jira 헤더만 붙이므로, 프로젝트 설정
     * 화면은 이걸로 "프로젝트로 돌아가기" 컨텍스트(브레드크럼 + 유사 사이드바)를 직접 그려준다.
     */
    static void render(HttpServletRequest req, HttpServletResponse resp, String decorator, String title,
                        String jsFileName, String mountDivId, String contextScript,
                        String beforeMountHtml, String afterMountHtml) throws IOException {
        String contextPath = req.getContextPath();
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html>");
        out.println("<head>");
        out.println("<meta name=\"decorator\" content=\"" + decorator + "\">");
        out.println("<title>" + escapeHtml(title) + "</title>");
        out.println("<link rel=\"stylesheet\" href=\"" + contextPath + PLUGIN_RESOURCE_BASE + "css/field-templates.css\">");
        out.println("</head>");
        out.println("<body>");
        if (beforeMountHtml != null) {
            out.println(beforeMountHtml);
        }
        out.println("<div id=\"" + mountDivId + "\" class=\"ft-app\"></div>");
        if (afterMountHtml != null) {
            out.println(afterMountHtml);
        }
        out.println("<script>" + contextScript + "</script>");
        out.println("<script src=\"" + contextPath + PLUGIN_RESOURCE_BASE + "js/rest-client.js\"></script>");
        out.println("<script src=\"" + contextPath + PLUGIN_RESOURCE_BASE + "js/" + jsFileName + "\"></script>");
        out.println("</body>");
        out.println("</html>");
    }

    static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
