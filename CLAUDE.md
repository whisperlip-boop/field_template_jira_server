# Field Templates for Jira — 자체 구현 클론

## 목표

상용 마켓플레이스 플러그인 "Field Templates for Jira"(net.gcore.plugins.fieldtemplates)와
동일한 기능을, 사내 Jira Server 8.17.1에서 동작하도록 **자체 구현**한다.

- 대상 원본: net.gcore.plugins.fieldtemplates 3.3.8 / 3.4.6 (jar 디컴파일 분석 완료)
- 목적: 원본 3.4.x에 추가된 "사용자·그룹 단위 템플릿 노출 제한" 기능이 필요한데, 벤더가
  3.4.x부터 최소 지원 Jira 버전을 8.20.0+로 올려서 8.17.1에는 설치 불가 → 직접 구현
- **DB 스키마·REST 경로·Java 패키지·프론트 JS/CSS는 전부 독자 설계.** 원본 파일을 복사하지
  않는다. 기능 동작만 동일하게 맞춘다.
- 마켓 업로드 계획 없음(사내 전용) → 라이선싱(UPM) 관련 코드는 만들지 않는다.

전체 기능 목록과 Phase별 구현 계획은 `/home/vuno/.claude/plans/async-weaving-prism.md` 참고.

## 환경

| 항목 | 값 |
|---|---|
| 개발 OS | Windows + WSL2 (Ubuntu) |
| Atlassian SDK | 8.2.7 (AMPS 8.1.2) — `/opt/atlassian-plugin-sdk` |
| Maven | 3.5.4 (SDK 내장) |
| JDK (빌드) | OpenJDK 8 — `/usr/lib/jvm/java-8-openjdk-amd64` |
| 대상 Jira | **8.17.1** (`pom.xml`의 `jira.version`) |
| Active Objects | **3.2.11** (Jira 8.17.1 배포판에 실제 번들된 버전으로 직접 확인·고정) |
| 대상 Confluence | 무관 (이 플러그인과 별개) |

### 테스트 인스턴스

- **개발용**: `atlas-run` → http://localhost:1990/jira
- **실기 검증용**: Docker Jira → 8.17.1로 교체 예정 (기존 8.13.0, 볼륨 `/data/docker/jira` 유지).
  같은 Docker 네트워크 `atlassian`에 postgresql(DB), confluence 함께 존재.
  `docker stop jira confluence`로 내려두고 개발할 것 (WSL 메모리 절약).

## 네이밍 규칙

| 항목 | 값 |
|---|---|
| groupId | `co.bskim.jira` |
| artifactId | `field-templates` |
| version | `1.0.3` |
| base package | `co.bskim.jira.fieldtemplate` |
| REST path | `/rest/field-templates/1.0` |

패키지·플러그인 키·i18n 키 등 모든 식별자는 `co.bskim.*`를 쓴다.

## pom.xml 버전 고정

```xml
<properties>
    <jira.version>8.17.1</jira.version>
    <ao.version>3.2.11</ao.version>
    <amps.version>8.1.2</amps.version>
</properties>
```

## 주요 명령어

```bash
export ATLAS_HOME=/opt/atlassian-plugin-sdk
export PATH="/opt/atlassian-plugin-sdk/apache-maven-3.5.4/bin:$PATH"
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

atlas-run          # 개발용 Jira 기동 (1990 포트, 첫 실행은 수 분~수십 분 — Jira 배포판 다운로드)
atlas-debug        # 디버그 포트 5005 열고 기동
atlas-package      # target/*.jar 빌드
atlas-cli          # 별도 터미널에서 실행 후 'pi' 입력 → 재빌드+핫 재설치
atlas-clean
```

개발 루프: 터미널1 `atlas-run` 유지 → 터미널2 `atlas-cli` → 코드 수정 후 `pi`

셸에 `ATLAS_HOME`이 기본으로 안 잡혀 있으면 `atlas-*` 스크립트가 내부적으로 참조하는
`file://${env.ATLAS_HOME}/repository`가 깨지므로 위 export를 항상 먼저 실행할 것
(원인: 이 WSL 세션엔 `/opt/atlassian-plugin-sdk/bin`이 PATH에 있어도 `ATLAS_HOME` 자체는
비대화형 쉘에서 비어 있을 수 있음 — 직접 겪은 문제).

## 규칙

- 프로젝트는 **WSL 파일시스템**(`~/dev/...`)에 둔다. `/mnt/c/...`는 I/O가 느려 Maven 빌드가 고통스럽다.
- 빌드용 JDK는 반드시 **8**. `JAVA_HOME`이 8을 가리키는지 확인.
- **JS/CSS를 포함해 원본 플러그인의 어떤 파일도 복사하지 않는다.** UX 컨셉(버튼 위치, 동작)만
  참고하고 코드는 전부 새로 작성한다.
- 원본 분석에서 확인한 파라미터 치환 토큰 표(`$reporter`, `$cf[123]` 등)와 위젯 주입 대상
  화면 셀렉터는 plan 문서에 정리되어 있으니 그대로 재사용해 기능 동등성을 맞춘다.
