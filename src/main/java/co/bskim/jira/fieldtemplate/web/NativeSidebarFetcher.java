package co.bskim.jira.fieldtemplate.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 좌측 프로젝트 사이드바를 우리가 손으로 근사(아이콘 클래스, 폭, 하단 고정 등)하다가 계속 어긋나는
 * 문제를 반복 겪음 — 대신 Jira의 진짜 프로젝트 설정 페이지(project-config summary)를 서버에서
 * 내부적으로 한 번 더 요청해서, 그 안에 BigPipe 데이터(WRM._unparsedData["sidebar-id"])로 들어있는
 * 진짜 사이드바 HTML을 그대로 뽑아 쓴다. Jira가 실제로 만든 결과이므로 아이콘/크기/정렬이 항상
 * 정확하고, 프로젝트 타입이 달라져도(Releases/Components 유무 등) 자동으로 맞음.
 *
 * 실패(네트워크 오류, 파싱 실패, 마크업 변경 등)하면 null을 반환 — 호출부(ProjectConfigServlet)가
 * 우리가 손으로 만든 백업 마크업으로 넘어간다.
 */
final class NativeSidebarFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(NativeSidebarFetcher.class);
    private static final long CACHE_TTL_MS = 10 * 60 * 1000;
    private static final Map<String, CachedSidebar> CACHE = new ConcurrentHashMap<>();

    private NativeSidebarFetcher() {
    }

    static String fetch(HttpServletRequest req, String projectKey) {
        CachedSidebar cached = CACHE.get(projectKey);
        if (cached != null && !cached.isExpired()) {
            return cached.html;
        }
        String html = fetchFresh(req, projectKey);
        if (html != null) {
            CACHE.put(projectKey, new CachedSidebar(html));
        }
        return html;
    }

    private static String fetchFresh(HttpServletRequest req, String projectKey) {
        try {
            // req.getServerPort()는 Host 헤더 기준(리버스 프록시 뒤에서는 사용자가 접속한 외부 포트,
            // 예: 443/80)이라 프록시 뒤에 있는 실 서버에서는 Jira(Tomcat)가 실제로 듣는 내부 포트와
            // 다르다 — 그 값으로 루프백 요청을 보내면 연결 자체가 실패해서 매번 fetch가 조용히 실패,
            // 폴백 사이드바로 넘어가는 문제가 있었다(실 서버에서 사용자가 실기로 발견: 좌패널이 우리가
            // 손으로 근사한 백업 마크업으로 계속 나옴). req.getLocalPort()는 이 요청을 실제로 받은
            // 서버 소켓 포트라 프록시 유무와 무관하게 항상 맞다.
            String url = "http://localhost:" + req.getLocalPort() + req.getContextPath()
                    + "/plugins/servlet/project-config/" + projectKey + "/summary";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("Cookie", cookieHeader(req));
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() != 200) {
                LOG.warn("NativeSidebarFetcher: fetch of " + url + " returned HTTP " + conn.getResponseCode()
                        + " — falling back to approximated sidebar for project " + projectKey);
                return null;
            }
            String body = readBody(conn);
            String rawEscaped = extractQuoted(body, "WRM._unparsedData[\"sidebar-id\"]=\"");
            if (rawEscaped == null) {
                return null;
            }
            // 실측(스크래치패드에 저장해둔 실제 curl 응답으로 파이썬 스크립트로 오프라인 검증함):
            // JS 문자열 리터럴 한 겹(WRM._unparsedData[...]="...") 안에 JSON 문자열이 또 한 겹 들어
            // 있고(2번 언이스케이프 필요), 그 JSON 문자열 값 자체가 앞뒤에 리터럴 큰따옴표를 가진
            // 문자열이라(3번째 JSON.stringify 레이어의 흔적) 언이스케이프 후에도 맨 앞/뒤에 " 문자가
            // 그대로 남는다 — 그 한 글자씩을 추가로 잘라내야 진짜 <section>...</section>만 남는다.
            String html = unescapeJsString(unescapeJsString(rawEscaped));
            if (html.length() >= 2 && html.charAt(0) == '"' && html.charAt(html.length() - 1) == '"') {
                html = html.substring(1, html.length() - 1);
            }
            if (!html.trim().startsWith("<section") || !html.contains("aui-sidebar")) {
                return null;
            }
            // 우리가 가져오는 시점의 HTML은 BigPipe가 아직 완전히 채워 넣기 전 "로딩 중" 상태
            // (sidebar-pending 클래스, 아바타에 점선 로딩 스피너가 붙는 스타일)라서, 실제 사용자는
            // 순식간에 지나치는 이 상태가 우리 페이지에선 영영 안 없어짐(사용자가 실기로 확인) —
            // 해당 클래스만 지워서 최종(resolved) 상태처럼 보이게 한다.
            html = html.replace("sidebar-pending", "").replace("aui-sidebar  projects-sidebar",
                    "aui-sidebar projects-sidebar");
            return html;
        } catch (Exception e) {
            // 이전엔 여기서 예외를 완전히 삼켜서(원인 불명 상태로 폴백만 조용히 켜짐), 실 서버에서
            // 리버스 프록시 때문에 fetch가 매번 실패하는 문제를 로그로 못 잡고 사용자 스크린샷만
            // 보고 원인을 역추적해야 했다 — 다시 이런 일이 없도록 원인을 남긴다.
            LOG.warn("NativeSidebarFetcher: failed to fetch native sidebar for project " + projectKey
                    + " — falling back to approximated sidebar", e);
            return null;
        }
    }

    private static String cookieHeader(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Cookie c : cookies) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(c.getName()).append('=').append(c.getValue());
        }
        return sb.toString();
    }

    private static String readBody(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    /** startMarker 뒤에서 시작하는 JS 문자열 리터럴의 내용(이스케이프 그대로)을, 닫는 따옴표까지 읽는다. */
    private static String extractQuoted(String haystack, String startMarker) {
        int idx = haystack.indexOf(startMarker);
        if (idx < 0) {
            return null;
        }
        int i = idx + startMarker.length();
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (; i < haystack.length(); i++) {
            char c = haystack.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                sb.append(c);
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return null; // 닫는 따옴표를 못 찾음
    }

    private static String unescapeJsString(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"':
                        sb.append('"');
                        i++;
                        break;
                    case '\\':
                        sb.append('\\');
                        i++;
                        break;
                    case '/':
                        sb.append('/');
                        i++;
                        break;
                    case 'n':
                        sb.append('\n');
                        i++;
                        break;
                    case 'r':
                        sb.append('\r');
                        i++;
                        break;
                    case 't':
                        sb.append('\t');
                        i++;
                        break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            String hex = s.substring(i + 2, i + 6);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                        break;
                    default:
                        sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final class CachedSidebar {
        final String html;
        final long fetchedAt;

        CachedSidebar(String html) {
            this.html = html;
            this.fetchedAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - fetchedAt > CACHE_TTL_MS;
        }
    }
}
