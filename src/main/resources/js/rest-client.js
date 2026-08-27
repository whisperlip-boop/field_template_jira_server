(function (window) {
    "use strict";

    function contextPath() {
        return (window.AJS && AJS.contextPath) ? AJS.contextPath() : "";
    }

    function request(method, path, body) {
        var url = contextPath() + "/rest/field-templates/1.0" + path;
        var opts = {
            method: method,
            credentials: "same-origin",
            headers: {
                "X-Atlassian-Token": "no-check"
            }
        };
        if (body !== undefined) {
            opts.headers["Content-Type"] = "application/json";
            opts.body = JSON.stringify(body);
        }
        return fetch(url, opts).then(function (resp) {
            if (resp.status === 204) {
                return null;
            }
            if (!resp.ok) {
                return resp.text().then(function (text) {
                    var message = text;
                    try {
                        var parsed = JSON.parse(text);
                        message = parsed.message || text;
                    } catch (e) { /* not JSON, use raw text */ }
                    throw new Error("HTTP " + resp.status + ": " + message);
                });
            }
            var contentType = resp.headers.get("Content-Type") || "";
            return contentType.indexOf("application/json") >= 0 ? resp.json() : null;
        });
    }

    window.FieldTemplatesRest = {
        get: function (path) {
            return request("GET", path);
        },
        post: function (path, body) {
            return request("POST", path, body === undefined ? {} : body);
        },
        put: function (path, body) {
            return request("PUT", path, body === undefined ? {} : body);
        },
        del: function (path) {
            return request("DELETE", path);
        }
    };
})(window);
