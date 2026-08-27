package co.bskim.jira.fieldtemplate.rest;

import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** TemplateRestriction(USER/GROUP) 등록 시 쓰는 사용자/그룹 검색. 사내 전용 규모라 전체 목록 필터링으로 단순화. */
@Path("/typeahead")
@Produces(MediaType.APPLICATION_JSON)
public class TypeaheadResource {

    private static final int MAX_RESULTS = 20;

    private final UserManager userManager;
    private final GroupManager groupManager;

    @Inject
    public TypeaheadResource(@ComponentImport UserManager userManager, @ComponentImport GroupManager groupManager) {
        this.userManager = userManager;
        this.groupManager = groupManager;
    }

    @GET
    @Path("/users")
    public Response users(@QueryParam("query") String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<Entry> results = userManager.getAllUsers().stream()
                .filter(u -> matches(needle, u.getUsername(), u.getDisplayName()))
                .sorted(Comparator.comparing(ApplicationUser::getDisplayName))
                .limit(MAX_RESULTS)
                .map(u -> new Entry(u.getKey(), u.getDisplayName() + " (" + u.getUsername() + ")"))
                .collect(Collectors.toList());
        return Response.ok(results).build();
    }

    @GET
    @Path("/groups")
    public Response groups(@QueryParam("query") String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<Entry> results = groupManager.getAllGroups().stream()
                .filter(g -> matches(needle, g.getName()))
                .sorted(Comparator.comparing(Group::getName))
                .limit(MAX_RESULTS)
                .map(g -> new Entry(g.getName(), g.getName()))
                .collect(Collectors.toList());
        return Response.ok(results).build();
    }

    private boolean matches(String needle, String... haystacks) {
        if (needle.isEmpty()) {
            return true;
        }
        for (String haystack : haystacks) {
            if (haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public static class Entry {
        public String key;
        public String label;

        public Entry(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }
}
