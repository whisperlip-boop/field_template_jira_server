/**
 * 이슈 Create/Edit/Transition 화면에 "Insert Template" 버튼을 주입한다.
 *
 * 최초 버전은 컨테이너(다이얼로그 루트)가 DOM에 나타나는 순간 딱 한 번만 컨텍스트(#pid/#issuetype)를
 * 읽고 끝냈는데, Jira의 Create 다이얼로그는 루트 div가 먼저 빈 채로 삽입되고 실제 필드(#pid,
 * #issuetype, #description 등)는 그 뒤 AJAX로 채워지는 경우가 있어 경쟁 조건으로 버튼이 전혀 안
 * 뜨는 문제가 있었다(사용자가 실제 화면에서 확인). 아래는 컨테이너별로 로컬 MutationObserver를 붙여
 * 내부 콘텐츠가 채워질 때마다 재시도하고, 프로젝트/이슈타입 select의 change 이벤트에도 재시도하도록
 * 고쳤다.
 */
(function () {
    "use strict";

    var REST = window.FieldTemplatesRest;
    if (!REST) {
        return;
    }

    var CREATE_SELECTORS = ["#issue-create", "#create-subtask-dialog", "#create-issue-dialog",
        "#prefillable-create-issue-dialog", "#create-linked-issue-dialog"];
    var EDIT_SELECTORS = ["#issue-edit", "#edit-issue-dialog"];
    var TRANSITION_SELECTORS = ["#issue-workflow-transition"];

    var wiredContainers = new WeakSet();

    function contextPath() {
        return (window.AJS && AJS.contextPath) ? AJS.contextPath() : "";
    }

    function jiraCoreGet(path) {
        return fetch(contextPath() + path, {credentials: "same-origin"}).then(function (r) {
            if (!r.ok) throw new Error("HTTP " + r.status);
            return r.json();
        });
    }

    function debounce(fn, wait) {
        var timer = null;
        return function () {
            clearTimeout(timer);
            timer = setTimeout(fn, wait);
        };
    }

    function scan(root) {
        findContainer(root, CREATE_SELECTORS, "CREATE");
        findContainer(root, EDIT_SELECTORS, "EDIT");
        findContainer(root, TRANSITION_SELECTORS, "TRANSITION");
    }

    function matchesSelector(el, sel) {
        var fn = el.matches || el.webkitMatchesSelector || el.msMatchesSelector;
        return !!fn && fn.call(el, sel);
    }

    /**
     * Element.querySelector()는 자기 자신은 검사하지 않고 자손만 검사한다. Jira가
     * `#create-issue-dialog`를 다이얼로그 콘텐츠까지 전부 채워진 채로 한 번에 DOM에 삽입하면,
     * MutationObserver의 addedNodes로 들어오는 노드가 바로 그 컨테이너 자신이라
     * `node.querySelector("#create-issue-dialog")`가 항상 null을 반환해서 컨테이너를 영영 못
     * 찾는 버그가 있었다(실기 콘솔 테스트로 확인) — root 자신이 셀렉터에 매칭되는 경우도 확인한다.
     */
    function findContainer(root, selectors, screenType) {
        selectors.forEach(function (sel) {
            var container = null;
            if (root.nodeType === 1 && matchesSelector(root, sel)) {
                container = root;
            } else if (root.querySelector) {
                container = root.querySelector(sel);
            }
            if (container && !wiredContainers.has(container)) {
                wiredContainers.add(container);
                wireContainer(container, screenType);
            }
        });
    }

    /** 컨테이너가 처음 나타난 시점엔 내부 필드가 비어있을 수 있으므로, 콘텐츠 변화를 계속 지켜보며 재시도한다. */
    function wireContainer(container, screenType) {
        var attempt = debounce(function () {
            setupContainer(container, screenType);
        }, 150);

        attempt();

        var localObserver = new MutationObserver(attempt);
        localObserver.observe(container, {childList: true, subtree: true});
    }

    function setupContainer(container, screenType) {
        resolveContext(container, screenType).then(function (ctx) {
            if (!ctx || !ctx.projectKey || !ctx.issueTypeId) {
                return;
            }
            attachChangeListeners(container, screenType);
            return REST.get("/fields?projectKey=" + encodeURIComponent(ctx.projectKey) +
                "&issueTypeId=" + encodeURIComponent(ctx.issueTypeId)).then(function (fields) {
                (fields || []).forEach(function (field) {
                    injectButton(container, field, ctx, screenType);
                });
            });
        }).catch(function (err) {
            if (window.console) console.warn("[field-templates] widget setup failed:", err);
        });
    }

    /** CREATE 화면에서 프로젝트/이슈타입을 바꾸면 그에 맞는 필드 목록으로 다시 계산해야 한다. */
    function attachChangeListeners(container, screenType) {
        if (screenType !== "CREATE" || container.getAttribute("data-ft-listeners")) {
            return;
        }
        container.setAttribute("data-ft-listeners", "1");
        var pid = container.querySelector("[name='pid']");
        var issuetype = container.querySelector("[name='issuetype']");
        [pid, issuetype].forEach(function (fieldEl) {
            if (fieldEl) {
                fieldEl.addEventListener("change", function () {
                    setupContainer(container, screenType);
                });
            }
        });
    }

    /**
     * CREATE: 실제 Jira Create 다이얼로그(atlas-run17 8.17.1에서 직접 HTML을 떠서 확인함)에서
     * 프로젝트/이슈타입 필드는 `<select>`가 아니라 `<input id="project" name="pid" value="<프로젝트
     * 숫자 ID>">` / `<input id="issuetype" name="issuetype" value="<이슈타입 숫자 ID>">` 형태의
     * 숨겨진 입력(AUI 커스텀 피커의 백킹 필드)이다. `id`는 렌더링 경로에 따라 없을 수도 있어(예:
     * 프로젝트가 URL로 고정된 진입 경로) `name` 속성으로만 찾는다. value는 프로젝트 "키"가 아니라
     * 숫자 프로젝트 ID라서 코어 REST로 한 번 더 키를 조회해야 한다.
     * EDIT/TRANSITION: AJS.Meta issue-key로 코어 REST 조회.
     */
    function resolveContext(container, screenType) {
        if (screenType === "CREATE") {
            var pid = container.querySelector("[name='pid']");
            var issuetype = container.querySelector("[name='issuetype']");
            var projectId = pid ? pid.value : null;
            var issueTypeId = issuetype ? issuetype.value : null;
            if (!projectId || !issueTypeId) {
                return Promise.resolve(null);
            }
            return jiraCoreGet("/rest/api/2/project/" + encodeURIComponent(projectId)).then(function (project) {
                return {projectKey: project.key, issueTypeId: issueTypeId, issueKey: null};
            }).catch(function () {
                return null;
            });
        }

        var issueKey = (window.AJS && AJS.Meta) ? AJS.Meta.get("issue-key") : null;
        if (!issueKey) {
            return Promise.resolve(null);
        }
        return jiraCoreGet("/rest/api/2/issue/" + encodeURIComponent(issueKey) + "?fields=project,issuetype")
            .then(function (issue) {
                return {
                    projectKey: issue.fields.project.key,
                    issueTypeId: issue.fields.issuetype.id,
                    issueKey: issueKey
                };
            });
    }

    var IMAGE_BASE = null;
    function imageBase() {
        if (IMAGE_BASE === null) {
            IMAGE_BASE = contextPath() + "/download/resources/co.bskim.jira.field-templates:field-templates-resources/images/";
        }
        return IMAGE_BASE;
    }

    function createButton() {
        var button = document.createElement("button");
        button.type = "button";
        button.className = "aui-button aui-button-subtle ft-insert-button";
        button.title = "Insert Template";
        button.setAttribute("aria-label", "Insert Template");
        var icon = document.createElement("img");
        icon.src = imageBase() + "template-button.svg";
        icon.alt = "";
        button.appendChild(icon);
        return button;
    }

    /**
     * wiki 렌더러가 적용된 필드(예: description)는 실제 입력창 위에 자체 툴바(.wiki-edit-toolbar)를
     * 별도로 그리는데, 그 툴바는 textarea의 자손이 아니라 형제라 querySelector로는 못 찾는다 —
     * textarea를 감싸는 `.wiki-edit-content`(또는 id가 "-wiki-edit"로 끝나는 컨테이너)의 부모까지
     * 올라가서 찾는다.
     */
    function findWikiToolbar(target) {
        if (!target.closest) {
            return null;
        }
        var content = target.closest('.wiki-edit-content, [id$="-wiki-edit"]');
        var scope = content && content.parentElement ? content.parentElement : null;
        return scope ? scope.querySelector(".wiki-edit-toolbar") : null;
    }

    /**
     * 우리 버튼을 이미 있는 툴바 버튼들과 같은 top/height로 맞춰서 나란히 정렬되게 하고, "+"(더보기)
     * 버튼 오른쪽에 작은 간격을 두고 배치한다.
     *
     * top을 CSS 고정값이나 toolbar.getBoundingClientRect() 기준으로 계산하면 항상 일정 픽셀만큼
     * 어긋났다(실기 좌표 덤프로 확인) — `.wiki-edit-toolbar`가 position:sticky라 containing block
     * 기준점이 getBoundingClientRect가 보여주는 "현재 위치"와 다른 것으로 추정된다. 이 문제는 top
     * 축에만 있는 게 아니라 left/right 축도 똑같이 겪는다는 걸 나중에 확인함(CSS `right:20px`를
     * 줬는데 실제로는 툴바 우측 끝에서 100px 넘게 떨어진 곳에 렌더링됐음) — 그래서 top과 마찬가지로
     * left도 "지금 실제로 어디 그려지는지 측정 → 목표 위치와의 차이만큼 이동"하는 델타 보정으로
     * 통일한다. 컨테이너의 실제 좌표계가 뭐가 됐든 이 방식은 항상 맞다.
     */
    function alignToToolbarSibling(button, toolbar) {
        var realButtons = [];
        var candidates = toolbar.querySelectorAll("a.aui-button, button");
        for (var i = 0; i < candidates.length; i++) {
            var candidate = candidates[i];
            if (candidate === button || button.contains(candidate)) {
                continue;
            }
            if (candidate.getBoundingClientRect().height > 0) {
                realButtons.push(candidate);
            }
        }
        if (realButtons.length === 0) {
            button.style.top = "50%";
            button.style.transform = "translateY(-50%)";
            return;
        }
        var sibling = realButtons[0];
        // 툴바 마지막 버튼은 보통 "툴바 숨기기" 토글이라, 그 바로 앞(있으면)을 "+"(더보기) 버튼으로
        // 삼아 그 옆에 배치한다 — 없으면 마지막 버튼 옆으로 대체.
        var anchor = realButtons.length >= 2 ? realButtons[realButtons.length - 2] : realButtons[realButtons.length - 1];

        var beforeRect = button.getBoundingClientRect();
        var siblingRect = sibling.getBoundingClientRect();
        var topDelta = siblingRect.top - beforeRect.top;
        var currentTop = parseFloat(button.style.top) || 0;
        button.style.top = (currentTop + topDelta) + "px";
        button.style.height = siblingRect.height + "px";

        var GAP = 8;
        var anchorRect = anchor.getBoundingClientRect();
        var afterTopRect = button.getBoundingClientRect(); // top 조정 후 다시 측정(left는 영향 없어야 하지만 안전하게)
        var desiredLeft = anchorRect.right + GAP;
        var leftDelta = desiredLeft - afterTopRect.left;
        var currentLeft = parseFloat(button.style.left) || 0;
        button.style.right = "auto"; // CSS의 고정 right 값이 left와 동시에 적용되지 않도록
        button.style.left = (currentLeft + leftDelta) + "px";
    }

    /** 툴바가 없는 일반 입력 필드는 필드 우측 바깥에 작은 아이콘 버튼을 붙인다. */
    function positionInline(button, target) {
        var parent = target.parentNode;
        if (window.getComputedStyle(parent).position === "static") {
            parent.style.position = "relative";
        }
        button.style.position = "absolute";
        button.style.top = target.offsetTop + "px";
        button.style.left = (target.offsetLeft + target.offsetWidth + 8) + "px";
    }

    function injectButton(container, field, ctx, screenType) {
        var target = container.querySelector("#" + cssEscape(field.id));
        if (!target || target.getAttribute("data-ft-injected")) {
            return;
        }
        target.setAttribute("data-ft-injected", "1");

        maybeApplyDefault(target, field, ctx, screenType);

        var button = createButton();
        button.addEventListener("click", function (e) {
            e.preventDefault();
            openPicker(button, target, field, ctx, screenType);
        });

        var toolbar = findWikiToolbar(target);
        if (toolbar) {
            // float도, top:50%로 툴바 자체 높이 기준 중앙정렬도 다른 툴바 아이콘들과 반 칸씩
            // 어긋났다(사용자가 실제 화면에서 확인) — 툴바 내부 padding/line-height를 정확히 몰라도
            // 안전하게 맞추려고, 이미 있는 툴바 버튼 하나의 실제 렌더링 위치(getBoundingClientRect)를
            // 그대로 복사해서 우리 버튼의 top/height를 맞춘다.
            if (window.getComputedStyle(toolbar).position === "static") {
                toolbar.style.position = "relative";
            }
            button.classList.add("ft-insert-button-toolbar");
            toolbar.appendChild(button);
            alignToToolbarSibling(button, toolbar);
        } else if (target.parentNode) {
            target.parentNode.appendChild(button);
            positionInline(button, target);
        }
    }

    function buildInsertQuery(field, ctx, screenType) {
        return "/insert?projectKey=" + encodeURIComponent(ctx.projectKey) +
            "&fieldId=" + encodeURIComponent(field.id) +
            "&issueTypeId=" + encodeURIComponent(ctx.issueTypeId) +
            "&screenType=" + encodeURIComponent(screenType) +
            (ctx.issueKey ? "&issueKey=" + encodeURIComponent(ctx.issueKey) : "");
    }

    /**
     * "Use as Default" 템플릿은 화면이 뜨는 시점에 사용자가 고르지 않아도 자동으로 필드에 채워지는
     * 기능이다(원본 분석 결과 — CREATE류 화면에서만, 그리고 필드가 비어 있을 때만 적용됨을 확인).
     * EDIT/TRANSITION 화면에서는 이미 값이 들어있는 필드를 덮어쓰면 안 되므로 적용하지 않는다.
     */
    function maybeApplyDefault(target, field, ctx, screenType) {
        if (screenType !== "CREATE" || target.value) {
            return;
        }
        REST.get(buildInsertQuery(field, ctx, screenType)).then(function (templates) {
            if (target.value) {
                return; // 응답을 기다리는 사이 사용자가 이미 입력했을 수 있음
            }
            var def = (templates || []).filter(function (t) { return t.isDefault; })[0];
            if (def) {
                insertIntoField(target, def.text);
                REST.post("/insert/" + def.source + "/" + def.id + "/use").catch(function () {});
            }
        }).catch(function () {});
    }

    var openPopup = null;

    function organizeTemplates(templates) {
        var ungrouped = [];
        var groupOrder = [];
        var groupMap = {};
        (templates || []).forEach(function (t) {
            if (t.groupId != null) {
                var key = String(t.groupId);
                if (!groupMap[key]) {
                    groupMap[key] = {name: t.groupName || "", items: []};
                    groupOrder.push(key);
                }
                groupMap[key].items.push(t);
            } else {
                ungrouped.push(t);
            }
        });
        return {ungrouped: ungrouped, groupOrder: groupOrder, groupMap: groupMap};
    }

    function createPickerItem(t, targetField) {
        var item = document.createElement("div");
        item.className = "ft-picker-item";
        item.style.padding = "8px 12px";
        item.style.cursor = "pointer";
        item.style.borderLeft = "4px solid " + (t.color || "#ccc");
        item.textContent = t.title + (t.isDefault ? " (Default)" : "");
        item.addEventListener("mouseenter", function () { item.style.background = "#f4f5f7"; });
        item.addEventListener("mouseleave", function () { item.style.background = ""; });
        item.addEventListener("click", function () {
            insertIntoField(targetField, t.text);
            REST.post("/insert/" + t.source + "/" + t.id + "/use").catch(function () {});
            closePopup();
        });
        return item;
    }

    function openPicker(anchor, targetField, field, ctx, screenType) {
        closePopup();

        var query = buildInsertQuery(field, ctx, screenType);

        REST.get(query).then(function (templates) {
            var popup = document.createElement("div");
            popup.className = "aui-list ft-picker-popup";
            popup.style.position = "absolute";
            popup.style.zIndex = "3000";
            popup.style.background = "#fff";
            popup.style.border = "1px solid #ccc";
            popup.style.borderRadius = "3px";
            popup.style.boxShadow = "0 4px 10px rgba(0,0,0,0.15)";
            popup.style.minWidth = "220px";
            popup.style.maxHeight = "300px";
            popup.style.overflowY = "auto";

            if (!templates || templates.length === 0) {
                var empty = document.createElement("div");
                empty.style.padding = "8px 12px";
                empty.style.color = "#6b778c";
                empty.textContent = "No templates available.";
                popup.appendChild(empty);
            } else {
                var organized = organizeTemplates(templates);
                organized.ungrouped.forEach(function (t) {
                    popup.appendChild(createPickerItem(t, targetField));
                });
                organized.groupOrder.forEach(function (key) {
                    var group = organized.groupMap[key];
                    var header = document.createElement("div");
                    header.className = "ft-picker-group-header";
                    header.style.padding = "6px 12px";
                    header.style.fontWeight = "bold";
                    header.style.fontSize = "11px";
                    header.style.color = "#6b778c";
                    header.style.background = "#f4f5f7";
                    header.textContent = group.name;
                    popup.appendChild(header);
                    group.items.forEach(function (t) {
                        var item = createPickerItem(t, targetField);
                        item.style.paddingLeft = "20px";
                        popup.appendChild(item);
                    });
                });
            }

            document.body.appendChild(popup);
            var rect = anchor.getBoundingClientRect();
            popup.style.top = (rect.bottom + window.scrollY) + "px";
            popup.style.left = (rect.left + window.scrollX) + "px";
            openPopup = popup;

            setTimeout(function () {
                document.addEventListener("click", onOutsideClick);
            }, 0);
        }).catch(function (err) {
            if (window.console) console.warn("[field-templates] failed to load templates:", err);
        });
    }

    function onOutsideClick(e) {
        if (openPopup && !openPopup.contains(e.target)) {
            closePopup();
        }
    }

    function closePopup() {
        if (openPopup) {
            openPopup.parentNode.removeChild(openPopup);
            openPopup = null;
            document.removeEventListener("click", onOutsideClick);
        }
    }

    function insertIntoField(field, text) {
        if (field.tagName === "TEXTAREA" || (field.tagName === "INPUT" && field.type === "text")) {
            var start = field.selectionStart != null ? field.selectionStart : field.value.length;
            var end = field.selectionEnd != null ? field.selectionEnd : field.value.length;
            field.value = field.value.slice(0, start) + text + field.value.slice(end);
            field.focus();
            field.dispatchEvent(new Event("change", {bubbles: true}));
            field.dispatchEvent(new Event("input", {bubbles: true}));
        } else if (window.console) {
            console.warn("[field-templates] unsupported field type for insertion (e.g. WYSIWYG editor) — needs manual check:", field);
        }
    }

    function cssEscape(id) {
        return window.CSS && CSS.escape ? CSS.escape(id) : id.replace(/[^a-zA-Z0-9_-]/g, "\\$&");
    }

    var globalObserver = new MutationObserver(function (mutations) {
        mutations.forEach(function (m) {
            m.addedNodes && m.addedNodes.forEach(function (node) {
                if (node.nodeType === 1) {
                    scan(node);
                }
            });
        });
    });

    document.addEventListener("DOMContentLoaded", function () {
        scan(document);
        globalObserver.observe(document.body, {childList: true, subtree: true});
        // 위 MutationObserver는 "새 노드가 추가되는" 경우만 잡는다. Jira가 다이얼로그 컨테이너를
        // 재사용하면서 id 속성만 바꿔 달거나(attribute 변경, childList 이벤트가 아님) 콘텐츠를
        // 우리가 예상 못한 순서로 채우는 경우까지 전부 대응하긴 어려워서, 문서 전체를 주기적으로
        // 다시 스캔하는 폴백을 둔다. wiredContainers WeakSet이 중복 처리를 막아주므로 반복 호출해도
        // 안전하고 비용도 낮다(1초마다 querySelector 8번).
        setInterval(function () {
            scan(document);
        }, 1000);
    });
})();
