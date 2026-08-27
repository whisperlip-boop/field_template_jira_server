package co.bskim.jira.fieldtemplate.service.impl;

import co.bskim.jira.fieldtemplate.model.Template;
import co.bskim.jira.fieldtemplate.model.TemplateUsageStat;
import co.bskim.jira.fieldtemplate.service.TemplateStatisticsService;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.DBParam;
import net.java.ao.Query;
import javax.inject.Inject;
import com.atlassian.plugin.spring.scanner.annotation.component.JiraComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@JiraComponent
@ExportAsService(TemplateStatisticsService.class)
public class TemplateStatisticsServiceImpl implements TemplateStatisticsService {

    private final ActiveObjects ao;

    @Inject
    public TemplateStatisticsServiceImpl(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
    }

    @Override
    public void recordUsage(int templateId) {
        Template template = ao.get(Template.class, templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }

        int currentCount = template.getUsageCount() == null ? 0 : template.getUsageCount();
        template.setUsageCount(currentCount + 1);
        template.save();

        TemplateUsageStat[] existing = ao.find(TemplateUsageStat.class, Query.select().where("TEMPLATE_ID = ?", templateId));
        TemplateUsageStat stat;
        if (existing.length > 0) {
            stat = existing[0];
            stat.setCount((stat.getCount() == null ? 0 : stat.getCount()) + 1);
        } else {
            stat = ao.create(TemplateUsageStat.class,
                    new DBParam("TEMPLATE_ID", template),
                    new DBParam("FIELD_ID", template.getFieldId()));
            stat.setCount(1);
        }
        stat.save();
    }

    @Override
    public List<TemplateUsageStat> findTopByField(String fieldId, int limit) {
        TemplateUsageStat[] stats = ao.find(TemplateUsageStat.class, Query.select()
                .where("FIELD_ID = ?", fieldId)
                .order("COUNT DESC")
                .limit(limit));
        return new ArrayList<>(Arrays.asList(stats));
    }
}
