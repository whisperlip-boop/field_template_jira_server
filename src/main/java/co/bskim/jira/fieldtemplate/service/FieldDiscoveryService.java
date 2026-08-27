package co.bskim.jira.fieldtemplate.service;

import java.util.List;

/** 프로젝트+이슈타입에서 템플릿을 등록할 수 있는 텍스트 필드(시스템/커스텀) 목록 조회. */
public interface FieldDiscoveryService {

    List<FieldDescriptor> findTextFields(String projectKey, String issueTypeId);

    class FieldDescriptor {
        public final String id;
        public final String name;
        public final boolean custom;

        public FieldDescriptor(String id, String name, boolean custom) {
            this.id = id;
            this.name = name;
            this.custom = custom;
        }
    }
}
