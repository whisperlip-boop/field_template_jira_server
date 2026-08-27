(function () {
    "use strict";

    var REST = window.FieldTemplatesRest;
    var projectKey = window.FieldTemplatesContext.projectKey;
    var root = document.getElementById("co-bskim-field-templates-project-config");

    var state = {
        config: null,
        fields: [],
        selectedFieldId: null,
        templates: [],
        groups: [],
        stats: [],
        editing: null, // Template object being edited, or {isNew:true} for a new one
        copyOpen: false,
        copyCandidates: [], // 현재 선택된 필드에 템플릿이 있는 다른 프로젝트 키 목록
        copySourceProjectKey: null,
        copyPreview: null, // {sourceTemplateCount, conflict}
        copyOverwrite: false
    };

    function el(tag, attrs, children) {
        var e = document.createElement(tag);
        attrs = attrs || {};
        Object.keys(attrs).forEach(function (key) {
            if (key === "class") {
                e.className = attrs[key];
            } else if (key === "text") {
                e.textContent = attrs[key];
            } else if (key.indexOf("on") === 0) {
                e.addEventListener(key.substring(2), attrs[key]);
            } else if (attrs[key] != null) {
                e.setAttribute(key, attrs[key]);
            }
        });
        (children || []).forEach(function (c) {
            if (c) e.appendChild(c);
        });
        return e;
    }

    /** hex 코드 텍스트 대신 클릭 가능한 색상 스와치 목록을 그린다. */
    function colorPicker(colors, initial) {
        var current = initial || (colors[0] || "#DEEBFF");
        var container = el("div", {class: "ft-color-picker"}, []);

        function paint() {
            container.innerHTML = "";
            colors.forEach(function (c) {
                var selected = c === current;
                var swatch = el("span", {
                    class: "ft-color-swatch-option",
                    title: c,
                    style: "display:inline-block;width:22px;height:22px;border-radius:3px;margin:0 6px 6px 0;" +
                        "cursor:pointer;vertical-align:middle;background:" + c + ";" +
                        "border:2px solid " + (selected ? "#0052CC" : "transparent") + ";" +
                        "box-shadow:0 0 0 1px rgba(9,30,66,0.15);"
                });
                swatch.addEventListener("click", function () {
                    current = c;
                    paint();
                });
                container.appendChild(swatch);
            });
        }

        paint();
        container.getValue = function () { return current; };
        return container;
    }

    function loadAll() {
        root.innerHTML = "";
        root.appendChild(el("div", {class: "aui-message aui-message-info"}, [
            el("p", {text: "Loading..."})
        ]));

        Promise.all([
            REST.get("/config"),
            REST.get("/groups/project/" + encodeURIComponent(projectKey))
        ]).then(function (results) {
            state.config = results[0];
            state.groups = results[1];
            return loadFieldsAcrossIssueTypes();
        }).then(function () {
            if (!state.selectedFieldId && state.fields.length > 0) {
                state.selectedFieldId = state.fields[0].id;
            }
            return loadTemplates();
        }).then(render).catch(showError);
    }

    function loadFieldsAcrossIssueTypes() {
        var issueTypes = state.config.issueTypes || [];
        if (issueTypes.length === 0) {
            state.fields = [];
            return Promise.resolve();
        }
        return Promise.all(issueTypes.map(function (it) {
            return REST.get("/fields?projectKey=" + encodeURIComponent(projectKey) + "&issueTypeId=" + encodeURIComponent(it.id));
        })).then(function (lists) {
            var byId = {};
            lists.forEach(function (list) {
                (list || []).forEach(function (f) {
                    byId[f.id] = f;
                });
            });
            state.fields = Object.keys(byId).map(function (id) {
                return byId[id];
            });
        });
    }

    /** Copy 패널의 Source Project 목록 — 현재 선택된 필드에 실제로 템플릿이 있는 프로젝트만 남긴다. */
    function loadCopyCandidates() {
        if (!state.selectedFieldId) {
            state.copyCandidates = [];
            return Promise.resolve();
        }
        return REST.get("/copy/candidates?targetProjectKey=" + encodeURIComponent(projectKey) +
            "&fieldId=" + encodeURIComponent(state.selectedFieldId)).then(function (keys) {
            state.copyCandidates = keys || [];
        });
    }

    function loadTemplates() {
        if (!state.selectedFieldId) {
            state.templates = [];
            state.stats = [];
            return Promise.resolve();
        }
        return Promise.all([
            REST.get("/templates/project/" + encodeURIComponent(projectKey) + "/field/" + encodeURIComponent(state.selectedFieldId)),
            REST.get("/statistics/field/" + encodeURIComponent(state.selectedFieldId) + "?limit=5")
        ]).then(function (results) {
            state.templates = results[0];
            state.stats = results[1];
        });
    }

    function showError(err) {
        root.innerHTML = "";
        root.appendChild(el("div", {class: "aui-message aui-message-error"}, [
            el("p", {text: String(err.message || err)})
        ]));
    }

    function render() {
        root.innerHTML = "";
        root.appendChild(el("h2", {text: "Field Templates Settings"}));
        root.appendChild(renderFieldSelector());
        root.appendChild(renderStats());
        root.appendChild(renderTemplateList());
        if (state.editing) {
            root.appendChild(renderEditForm());
        }
        root.appendChild(renderCopySection());
    }

    /** 현재 Select Field에서 고른 필드의 템플릿만 대상 — 다른 필드 템플릿이 있는 프로젝트는 아예 목록에 안 나온다. */
    function renderCopySection() {
        var container = el("div", {class: "ft-edit-form aui", style: "margin-top: 24px;"}, []);
        var toggle = el("button", {
            class: "aui-button", text: state.copyOpen ? "Close Copy Panel" : "Copy From Another Project",
            onclick: function () {
                state.copyOpen = !state.copyOpen;
                if (state.copyOpen) {
                    loadCopyCandidates().then(render).catch(showError);
                } else {
                    render();
                }
            }
        });
        container.appendChild(toggle);

        if (!state.copyOpen) {
            return container;
        }

        var candidateProjects = (state.config.projects || []).filter(function (p) {
            return (state.copyCandidates || []).indexOf(p.id) >= 0;
        });

        if (candidateProjects.length === 0) {
            container.appendChild(el("p", {
                style: "margin-top: 12px; color: #6b778c;",
                text: "No other project has templates for the currently selected field."
            }));
            return container;
        }

        var sourceSelect = el("select", {class: "select"}, [el("option", {value: "", text: "(select)"})].concat(
            candidateProjects.map(function (p) {
                var opt = el("option", {value: p.id, text: p.id + " - " + p.name});
                if (p.id === state.copySourceProjectKey) opt.selected = true;
                return opt;
            })
        ));
        sourceSelect.addEventListener("change", function (e) {
            state.copySourceProjectKey = e.target.value || null;
            state.copyPreview = null;
        });

        var panel = el("div", {style: "margin-top: 12px;"}, [
            el("div", {class: "field-group"}, [el("label", {text: "Source Project"}), sourceSelect]),
            el("button", {
                class: "aui-button", text: "Preview",
                onclick: function () {
                    if (!state.copySourceProjectKey) return;
                    REST.get("/copy/preview?sourceProjectKey=" + encodeURIComponent(state.copySourceProjectKey) +
                        "&targetProjectKey=" + encodeURIComponent(projectKey) +
                        "&fieldId=" + encodeURIComponent(state.selectedFieldId)).then(function (preview) {
                        state.copyPreview = preview;
                        state.copyOverwrite = false;
                        render();
                    }).catch(showError);
                }
            })
        ]);
        container.appendChild(panel);

        if (state.copyPreview) {
            var p = state.copyPreview;
            var templates = p.templates || [];
            var summary = el("p", {}, []);
            summary.textContent = templates.length + " template(s) for the selected field will be copied." +
                (p.conflict ? " This project already has templates for this field." : "");
            container.appendChild(summary);

            container.appendChild(el("div", {style: "margin: 8px 0;"}, templates.map(function (t) {
                return el("div", {
                    style: "padding: 8px 10px; margin-bottom: 6px; border-left: 4px solid " + (t.color || "#ccc") +
                        "; background: #fff; border-radius: 2px;"
                }, [
                    el("div", {style: "font-weight: bold;", text: t.title}),
                    el("div", {style: "color: #6b778c; white-space: pre-wrap; margin-top: 4px;", text: t.text || ""})
                ]);
            })));

            if (p.conflict) {
                var overwriteCb = el("input", {type: "checkbox"});
                overwriteCb.checked = !!state.copyOverwrite;
                overwriteCb.addEventListener("change", function (e) { state.copyOverwrite = e.target.checked; });
                container.appendChild(el("div", {class: "field-group"}, [
                    el("label", {class: "ft-inline-check"}, [overwriteCb,
                        document.createTextNode(" Overwrite existing templates for this field (unchecked = cancel)")])
                ]));
            }

            container.appendChild(el("button", {
                class: "aui-button aui-button-primary", text: "Copy",
                disabled: templates.length === 0 ? "disabled" : null,
                onclick: function () {
                    if (p.conflict && !state.copyOverwrite) return;
                    REST.post("/copy", {
                        sourceProjectKey: state.copySourceProjectKey,
                        targetProjectKey: projectKey,
                        fieldId: state.selectedFieldId,
                        overwrite: !!state.copyOverwrite
                    }).then(function () {
                        state.copyOpen = false;
                        state.copyPreview = null;
                        return loadTemplates();
                    }).then(render).catch(showError);
                }
            }));
        }

        return container;
    }

    function renderFieldSelector() {
        var select = el("select", {
            class: "select",
            onchange: function (e) {
                state.selectedFieldId = e.target.value;
                state.copySourceProjectKey = null;
                state.copyPreview = null;
                loadTemplates().then(function () {
                    return state.copyOpen ? loadCopyCandidates() : null;
                }).then(render).catch(showError);
            }
        }, state.fields.map(function (f) {
            var opt = el("option", {value: f.id, text: f.name + (f.custom ? " (custom field)" : "")});
            if (f.id === state.selectedFieldId) opt.selected = true;
            return opt;
        }));
        return el("div", {class: "field-group"}, [
            el("label", {text: "Select Field"}),
            select
        ]);
    }

    function renderStats() {
        if (!state.stats || state.stats.length === 0) {
            return el("div", {}, []);
        }
        var items = state.stats.map(function (s) {
            return el("li", {text: s.templateTitle + " — " + s.count + " uses"});
        });
        return el("div", {class: "field-group"}, [
            el("label", {text: "Most Used Templates"}),
            el("ul", {}, items)
        ]);
    }

    function renderTemplateList() {
        var rows = state.templates.map(function (t, index) {
            return el("tr", {}, [
                el("td", {}, [el("span", {
                    class: "ft-color-swatch",
                    style: "display:inline-block;width:14px;height:14px;border-radius:2px;background:" + (t.color || "#ccc") + ";margin-right:6px;"
                }), document.createTextNode(t.title)]),
                el("td", {text: t.isDefault ? "Default" : ""}),
                el("td", {text: t.visible ? "" : "Hidden"}),
                el("td", {text: String(t.usageCount || 0)}),
                el("td", {}, [
                    el("button", {
                        class: "aui-button aui-button-link", text: "↑",
                        disabled: index === 0 ? "disabled" : null,
                        onclick: function () { moveTemplate(t, -1); }
                    }),
                    el("button", {
                        class: "aui-button aui-button-link", text: "↓",
                        disabled: index === state.templates.length - 1 ? "disabled" : null,
                        onclick: function () { moveTemplate(t, 1); }
                    }),
                    el("button", {
                        class: "aui-button aui-button-link", text: "Edit",
                        onclick: function () { state.editing = t; render(); }
                    }),
                    el("button", {
                        class: "aui-button aui-button-link", text: "Delete",
                        onclick: function () { deleteTemplate(t); }
                    })
                ])
            ]);
        });

        var table = el("table", {class: "aui"}, [
            el("thead", {}, [el("tr", {}, [
                el("th", {text: "Title"}), el("th", {text: ""}), el("th", {text: ""}),
                el("th", {text: "Uses"}), el("th", {text: ""})
            ])]),
            el("tbody", {}, rows)
        ]);

        return el("div", {}, [
            table,
            el("button", {
                class: "aui-button aui-button-primary", text: "New Template", style: "margin-top: 12px;",
                onclick: function () {
                    state.editing = {
                        isNew: true, title: "", color: (state.config.colors || ["#DEEBFF"])[0], text: "",
                        visible: true, isDefault: false, screenTypes: [], issueTypeIds: [], roleIds: [], restrictions: []
                    };
                    render();
                }
            })
        ]);
    }

    function moveTemplate(template, direction) {
        var ids = state.templates.map(function (t) { return t.id; });
        var idx = ids.indexOf(template.id);
        var swapWith = idx + direction;
        if (swapWith < 0 || swapWith >= ids.length) return;
        var tmp = ids[idx];
        ids[idx] = ids[swapWith];
        ids[swapWith] = tmp;
        REST.put("/templates/project/" + encodeURIComponent(projectKey) + "/field/" + encodeURIComponent(state.selectedFieldId) + "/order", ids)
            .then(loadTemplates).then(render).catch(showError);
    }

    function deleteTemplate(template) {
        if (!window.confirm("Delete template '" + template.title + "'?")) return;
        REST.del("/templates/" + template.id).then(loadTemplates).then(render).catch(showError);
    }

    function renderEditForm() {
        var t = state.editing;

        function field(labelText, inputEl) {
            return el("div", {class: "field-group"}, [el("label", {text: labelText}), inputEl]);
        }

        var titleInput = el("input", {class: "text", type: "text", value: t.title || ""});
        var textArea = el("textarea", {class: "textarea", rows: "6"}, []);
        textArea.value = t.text || "";
        var visibleCheckbox = el("input", {type: "checkbox"});
        visibleCheckbox.checked = t.visible !== false;
        var defaultCheckbox = el("input", {type: "checkbox"});
        defaultCheckbox.checked = !!t.isDefault;

        var colorPickerEl = colorPicker(state.config.colors || ["#DEEBFF"], t.color);

        var groupSelect = el("select", {class: "select"}, [el("option", {value: "", text: "(none)"})].concat(
            state.groups.map(function (g) {
                var opt = el("option", {value: String(g.id), text: g.name});
                if (t.groupId === g.id) opt.selected = true;
                return opt;
            })
        ));

        var screenTypeBoxes = ["CREATE", "EDIT", "TRANSITION"].map(function (st) {
            var cb = el("input", {type: "checkbox", value: st});
            cb.checked = (t.screenTypes || []).indexOf(st) >= 0;
            return el("label", {class: "ft-inline-check"}, [cb, document.createTextNode(" " + st)]);
        });

        var issueTypeBoxes = (state.config.issueTypes || []).map(function (it) {
            var cb = el("input", {type: "checkbox", value: it.id});
            cb.checked = (t.issueTypeIds || []).indexOf(it.id) >= 0;
            return el("label", {class: "ft-inline-check"}, [cb, document.createTextNode(" " + it.name)]);
        });

        var roleBoxes = (state.config.roles || []).map(function (r) {
            var cb = el("input", {type: "checkbox", value: String(r.id)});
            cb.checked = (t.roleIds || []).indexOf(r.id) >= 0;
            return el("label", {class: "ft-inline-check"}, [cb, document.createTextNode(" " + r.name)]);
        });

        var restrictionsContainer = el("div", {}, []);
        var restrictions = (t.restrictions || []).slice();
        renderRestrictions(restrictionsContainer, restrictions);

        var form = el("div", {class: "ft-edit-form aui"}, [
            el("h3", {text: t.isNew ? "New Template" : "Edit Template"}),
            field("Title", titleInput),
            field("Color", colorPickerEl),
            field("Text", textArea),
            field("Group", groupSelect),
            el("div", {class: "field-group"}, [el("label", {text: "Visible"}), visibleCheckbox]),
            el("div", {class: "field-group"}, [el("label", {text: "Use as Default"}), defaultCheckbox]),
            el("div", {class: "field-group"}, [el("label", {text: "Screen Types (all if empty)"})].concat(screenTypeBoxes)),
            el("div", {class: "field-group"}, [el("label", {text: "Issue Types (all if empty)"})].concat(issueTypeBoxes)),
            el("div", {class: "field-group"}, [el("label", {text: "Roles (all if empty)"})].concat(roleBoxes)),
            el("div", {class: "field-group"}, [el("label", {text: "Access Restrictions (none if empty)"}), restrictionsContainer,
                el("button", {
                    class: "aui-button", text: "+ Add Restriction",
                    onclick: function (e) {
                        e.preventDefault();
                        restrictions.push({type: "ANY_LOGGED_IN", targetKey: ""});
                        renderRestrictions(restrictionsContainer, restrictions);
                    }
                })
            ]),
            el("div", {class: "field-group"}, [
                el("button", {
                    class: "aui-button aui-button-primary", text: "Save",
                    onclick: function () {
                        var incomplete = restrictions.some(function (r) {
                            return (r.type === "USER" || r.type === "GROUP") && !r.targetKey;
                        });
                        if (incomplete) {
                            window.alert("Pick a user/group from the dropdown for each Access Restriction (or remove the incomplete row).");
                            return;
                        }
                        saveTemplate({
                            projectKey: projectKey,
                            fieldId: state.selectedFieldId,
                            title: titleInput.value,
                            color: colorPickerEl.getValue(),
                            text: textArea.value,
                            visible: visibleCheckbox.checked,
                            isDefault: defaultCheckbox.checked,
                            groupId: groupSelect.value ? Number(groupSelect.value) : null,
                            screenTypes: checkedValues(screenTypeBoxes),
                            issueTypeIds: checkedValues(issueTypeBoxes),
                            roleIds: checkedValues(roleBoxes).map(Number),
                            restrictions: restrictions.filter(function (r) { return r.type; })
                        });
                    }
                }),
                el("button", {
                    class: "aui-button", text: "Cancel",
                    onclick: function () { state.editing = null; render(); }
                })
            ])
        ]);
        return form;
    }

    /**
     * targetKey는 Jira의 내부 사용자 키(로그인명과 다를 수 있음 — matchesRestriction()이
     * user.getKey()로 비교함)라서 관리자가 손으로 정확히 타이핑하는 건 틀리기 쉽다(실제로 사용자가
     * 로그인명을 그대로 넣었다가 매칭이 안 되는 걸 겪음). TypeaheadResource(/typeahead/users,
     * /typeahead/groups)는 이미 만들어져 있었는데 이 화면에 연결이 안 되어 있었던 것 — Jira의 CC
     * 필드처럼 입력하면서 후보를 보여주고 클릭으로 정확한 key를 선택하게 한다.
     */
    function wireRestrictionTypeahead(input, r) {
        var dropdown = null;
        var debounceTimer = null;

        function closeDropdown() {
            if (dropdown && dropdown.parentNode) {
                dropdown.parentNode.removeChild(dropdown);
            }
            dropdown = null;
        }

        function showResults(entries) {
            closeDropdown();
            if (!entries || entries.length === 0) {
                return;
            }
            dropdown = el("div", {
                class: "aui-list ft-picker-popup",
                style: "position:absolute;z-index:3000;background:#fff;border:1px solid #ccc;border-radius:3px;" +
                    "box-shadow:0 4px 10px rgba(0,0,0,0.15);max-height:200px;overflow-y:auto;"
            }, entries.map(function (entry) {
                var item = el("div", {style: "padding:6px 10px;cursor:pointer;", text: entry.label});
                item.addEventListener("mousedown", function (e) {
                    e.preventDefault(); // blur보다 먼저 클릭이 처리되게
                    r.targetKey = entry.key;
                    input.value = entry.label;
                    closeDropdown();
                });
                item.addEventListener("mouseenter", function () { item.style.background = "#f4f5f7"; });
                item.addEventListener("mouseleave", function () { item.style.background = ""; });
                return item;
            }));
            document.body.appendChild(dropdown);
            var rect = input.getBoundingClientRect();
            dropdown.style.top = (rect.bottom + window.scrollY) + "px";
            dropdown.style.left = (rect.left + window.scrollX) + "px";
            dropdown.style.minWidth = rect.width + "px";
        }

        input.addEventListener("input", function () {
            r.targetKey = ""; // 목록에서 실제로 고르기 전까지는 무효한 상태로 취급
            clearTimeout(debounceTimer);
            if (r.type !== "USER" && r.type !== "GROUP") {
                return;
            }
            var query = input.value;
            debounceTimer = setTimeout(function () {
                var path = r.type === "USER" ? "/typeahead/users" : "/typeahead/groups";
                REST.get(path + "?query=" + encodeURIComponent(query)).then(showResults).catch(function () {});
            }, 200);
        });
        input.addEventListener("blur", function () {
            setTimeout(closeDropdown, 150);
        });
    }

    function renderRestrictions(container, restrictions) {
        container.innerHTML = "";
        restrictions.forEach(function (r, idx) {
            var typeSelect = el("select", {class: "select"}, ["ANY_LOGGED_IN", "USER", "GROUP"].map(function (opt) {
                var o = el("option", {value: opt, text: opt});
                if (opt === r.type) o.selected = true;
                return o;
            }));
            typeSelect.addEventListener("change", function (e) {
                r.type = e.target.value;
                r.targetKey = "";
                renderRestrictions(container, restrictions);
            });
            var rowChildren = [typeSelect];
            if (r.type === "USER" || r.type === "GROUP") {
                var targetInput = el("input", {
                    class: "text", type: "text",
                    placeholder: r.type === "USER" ? "Search user..." : "Search group..."
                });
                targetInput.value = r.targetKey || "";
                wireRestrictionTypeahead(targetInput, r);
                rowChildren.push(targetInput);
            }
            container.appendChild(el("div", {class: "ft-restriction-row"}, rowChildren.concat([
                el("button", {
                    class: "aui-button aui-button-link", text: "Delete",
                    onclick: function (e) {
                        e.preventDefault();
                        restrictions.splice(idx, 1);
                        renderRestrictions(container, restrictions);
                    }
                })
            ])));
        });
    }

    function checkedValues(labelsWithCheckboxes) {
        return labelsWithCheckboxes
            .map(function (label) { return label.querySelector("input"); })
            .filter(function (cb) { return cb.checked; })
            .map(function (cb) { return cb.value; });
    }

    function saveTemplate(payload) {
        if (!payload.title || !payload.title.trim()) {
            window.alert("Title is required.");
            return;
        }
        var isNew = state.editing.isNew;
        var promise = isNew
            ? REST.post("/templates", payload)
            : REST.put("/templates/" + state.editing.id, payload);
        promise.then(function () {
            state.editing = null;
            return loadTemplates();
        }).then(render).catch(showError);
    }

    loadAll();
})();
