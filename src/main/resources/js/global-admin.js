(function () {
    "use strict";

    var REST = window.FieldTemplatesRest;
    var root = document.getElementById("co-bskim-field-templates-global-admin");

    function contextPath() {
        return (window.AJS && AJS.contextPath) ? AJS.contextPath() : "";
    }

    function el(tag, attrs, children) {
        var e = document.createElement(tag);
        attrs = attrs || {};
        Object.keys(attrs).forEach(function (key) {
            if (key === "class") {
                e.className = attrs[key];
            } else if (key === "text") {
                e.textContent = attrs[key];
            } else if (attrs[key] != null) {
                e.setAttribute(key, attrs[key]);
            }
        });
        (children || []).forEach(function (c) { if (c) e.appendChild(c); });
        return e;
    }

    function render(entries) {
        root.innerHTML = "";
        root.appendChild(el("h2", {text: "Field Templates — Global Admin"}));

        if (!entries || entries.length === 0) {
            root.appendChild(el("p", {text: "No projects have templates yet."}));
            return;
        }

        var totalTemplates = entries.reduce(function (sum, e) { return sum + e.templateCount; }, 0);
        var totalGroups = entries.reduce(function (sum, e) { return sum + e.groupCount; }, 0);
        root.appendChild(el("p", {text: "Total " + entries.length + " projects, " + totalTemplates + " templates, " + totalGroups + " groups"}));

        var rows = entries.map(function (e) {
            var link = el("a", {
                href: contextPath() + "/plugins/servlet/field-templates/project-config?projectKey=" + encodeURIComponent(e.projectKey),
                text: e.projectKey + " - " + e.projectName
            });
            return el("tr", {}, [
                el("td", {}, [link]),
                el("td", {text: String(e.templateCount)}),
                el("td", {text: String(e.groupCount)})
            ]);
        });

        var table = el("table", {class: "aui"}, [
            el("thead", {}, [el("tr", {}, [
                el("th", {text: "Project"}), el("th", {text: "Templates"}), el("th", {text: "Groups"})
            ])]),
            el("tbody", {}, rows)
        ]);
        root.appendChild(table);
    }

    root.innerHTML = "<p>Loading...</p>";
    REST.get("/global/overview").then(render).catch(function (err) {
        root.innerHTML = "";
        root.appendChild(el("div", {class: "aui-message aui-message-error"}, [el("p", {text: String(err.message || err)})]));
    });
})();
