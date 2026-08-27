package co.bskim.jira.fieldtemplate.rest;

import co.bskim.jira.fieldtemplate.model.TemplateUsageStat;
import co.bskim.jira.fieldtemplate.service.TemplateStatisticsService;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/statistics")
@Produces(MediaType.APPLICATION_JSON)
public class StatisticsResource {

    private final TemplateStatisticsService statisticsService;

    @Inject
    public StatisticsResource(TemplateStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GET
    @Path("/field/{fieldId}")
    public Response topByField(@PathParam("fieldId") String fieldId, @QueryParam("limit") Integer limit) {
        List<TemplateUsageStat> stats = statisticsService.findTopByField(fieldId, limit == null ? 10 : limit);
        List<Entry> entries = stats.stream()
                .map(s -> new Entry(s.getTemplate().getID(), s.getTemplate().getTitle(), s.getFieldId(), s.getCount()))
                .collect(Collectors.toList());
        return Response.ok(entries).build();
    }

    public static class Entry {
        public int templateId;
        public String templateTitle;
        public String fieldId;
        public Integer count;

        public Entry(int templateId, String templateTitle, String fieldId, Integer count) {
            this.templateId = templateId;
            this.templateTitle = templateTitle;
            this.fieldId = fieldId;
            this.count = count;
        }
    }
}
