(function () {
    "use strict";

    var REST = window.FieldTemplatesRest;
    var root = document.getElementById("co-bskim-field-templates-personal-templates");

    var state = {
        templates: [],
        config: null,
        projects: [], // 브라우징 가능한 프로젝트 — Jira 코어 REST에서 그대로 가져옴(관리 권한 필요 없음)
        editing: null
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
        (children || []).forEach(function (c) { if (c) e.appendChild(c); });
        return e;
    }

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

    function jiraCoreGet(path) {
        var contextPath = (window.AJS && AJS.contextPath) ? AJS.contextPath() : "";
        return fetch(contextPath + path, {credentials: "same-origin"}).then(function (r) { return r.json(); });
    }

    function loadAll() {
        root.innerHTML = "";
        root.appendChild(el("p", {text: "Loading..."}));
        Promise.all([
            REST.get("/personal-templates"),
            REST.get("/config"),
            jiraCoreGet("/rest/api/2/project")
        ]).then(function (results) {
            state.templates = results[0];
            state.config = results[1];
            state.projects = results[2];
            render();
        }).catch(showError);
    }

    function showError(err) {
        root.innerHTML = "";
        root.appendChild(el("div", {class: "aui-message aui-message-error"}, [el("p", {text: String(err.message || err)})]));
    }

    function render() {
        root.innerHTML = "";
        root.appendChild(el("h2", {text: "My Templates"}));
        root.appendChild(renderList());
        if (state.editing) {
            root.appendChild(renderEditForm());
        }
    }

    function renderList() {
        var rows = state.templates.map(function (t, index) {
            return el("tr", {}, [
                el("td", {}, [el("span", {
                    style: "display:inline-block;width:14px;height:14px;border-radius:2px;background:" + (t.color || "#ccc") + ";margin-right:6px;"
                }), document.createTextNode(t.title)]),
                el("td", {text: t.visible ? "" : "Hidden"}),
                el("td", {}, [
                    el("button", {
                        class: "aui-button aui-button-link", text: "↑", disabled: index === 0 ? "disabled" : null,
                        onclick: function () { moveTemplate(t, -1); }
                    }),
                    el("button", {
                        class: "aui-button aui-button-link", text: "↓", disabled: index === state.templates.length - 1 ? "disabled" : null,
                        onclick: function () { moveTemplate(t, 1); }
                    }),
                    el("button", {class: "aui-button aui-button-link", text: "Edit", onclick: function () { state.editing = t; render(); }}),
                    el("button", {class: "aui-button aui-button-link", text: "Delete", onclick: function () { deleteTemplate(t); }})
                ])
            ]);
        });
        var table = el("table", {class: "aui"}, [
            el("thead", {}, [el("tr", {}, [el("th", {text: "Title"}), el("th", {text: ""}), el("th", {text: ""})])]),
            el("tbody", {}, rows)
        ]);
        return el("div", {}, [
            table,
            el("button", {
                class: "aui-button aui-button-primary", text: "New Template",
                onclick: function () {
                    state.editing = {
                        isNew: true, title: "", color: (state.config.colors || ["#DEEBFF"])[0], text: "",
                        visible: true, isAllProjects: true, isAllIssueTypes: true, projectKeys: [], issueTypeIds: []
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
        REST.put("/personal-templates/order", ids).then(function () {
            return REST.get("/personal-templates");
        }).then(function (list) {
            state.templates = list;
            render();
        }).catch(showError);
    }

    function deleteTemplate(template) {
        if (!window.confirm("Delete template '" + template.title + "'?")) return;
        REST.del("/personal-templates/" + template.id).then(function () {
            return REST.get("/personal-templates");
        }).then(function (list) {
            state.templates = list;
            render();
        }).catch(showError);
    }

    function checkedValues(labelsWithCheckboxes) {
        return labelsWithCheckboxes
            .map(function (label) { return label.querySelector("input"); })
            .filter(function (cb) { return cb.checked; })
            .map(function (cb) { return cb.value; });
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

        var colorPickerEl = colorPicker(state.config.colors || ["#DEEBFF"], t.color);

        var allProjectsCheckbox = el("input", {type: "checkbox"});
        allProjectsCheckbox.checked = t.isAllProjects !== false;
        var projectBoxes = state.projects.map(function (p) {
            var cb = el("input", {type: "checkbox", value: p.key});
            cb.checked = (t.projectKeys || []).indexOf(p.key) >= 0;
            return el("label", {class: "ft-inline-check"}, [cb, document.createTextNode(" " + p.key + " - " + p.name)]);
        });

        var allIssueTypesCheckbox = el("input", {type: "checkbox"});
        allIssueTypesCheckbox.checked = t.isAllIssueTypes !== false;
        var issueTypeBoxes = (state.config.issueTypes || []).map(function (it) {
            var cb = el("input", {type: "checkbox", value: it.id});
            cb.checked = (t.issueTypeIds || []).indexOf(it.id) >= 0;
            return el("label", {class: "ft-inline-check"}, [cb, document.createTextNode(" " + it.name)]);
        });

        var form = el("div", {class: "ft-edit-form aui"}, [
            el("h3", {text: t.isNew ? "New Template" : "Edit Template"}),
            field("Title", titleInput),
            field("Color", colorPickerEl),
            field("Text", textArea),
            el("div", {class: "field-group"}, [el("label", {text: "Visible"}), visibleCheckbox]),
            el("div", {class: "field-group"}, [
                el("label", {text: "Available in All Projects"}), allProjectsCheckbox,
                el("div", {}, projectBoxes)
            ]),
            el("div", {class: "field-group"}, [
                el("label", {text: "Available for All Issue Types"}), allIssueTypesCheckbox,
                el("div", {}, issueTypeBoxes)
            ]),
            el("div", {class: "field-group"}, [
                el("button", {
                    class: "aui-button aui-button-primary", text: "Save",
                    onclick: function () {
                        if (!titleInput.value || !titleInput.value.trim()) {
                            window.alert("Title is required.");
                            return;
                        }
                        var payload = {
                            title: titleInput.value,
                            color: colorPickerEl.getValue(),
                            text: textArea.value,
                            visible: visibleCheckbox.checked,
                            isAllProjects: allProjectsCheckbox.checked,
                            isAllIssueTypes: allIssueTypesCheckbox.checked,
                            projectKeys: checkedValues(projectBoxes),
                            issueTypeIds: checkedValues(issueTypeBoxes)
                        };
                        var promise = t.isNew ? REST.post("/personal-templates", payload) : REST.put("/personal-templates/" + t.id, payload);
                        promise.then(function () {
                            state.editing = null;
                            return REST.get("/personal-templates");
                        }).then(function (list) {
                            state.templates = list;
                            render();
                        }).catch(showError);
                    }
                }),
                el("button", {class: "aui-button", text: "Cancel", onclick: function () { state.editing = null; render(); }})
            ])
        ]);
        return form;
    }

    loadAll();
})();
