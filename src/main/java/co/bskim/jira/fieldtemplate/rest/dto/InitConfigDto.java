package co.bskim.jira.fieldtemplate.rest.dto;

import java.util.List;

public class InitConfigDto {
    public List<Entry> issueTypes;
    public List<RoleEntry> roles;
    public List<Entry> projects;
    public List<String> colors;

    public static class Entry {
        public String id;
        public String name;

        public Entry(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class RoleEntry {
        public Long id;
        public String name;

        public RoleEntry(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
