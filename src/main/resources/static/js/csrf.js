(() => {
    "use strict";

    function readMeta(name) {
        const el = document.querySelector(`meta[name='${name}']`);
        return el ? el.getAttribute("content") : "";
    }

    function readHidden(id) {
        const el = document.getElementById(id);
        return el ? (el.value ?? "") : "";
    }

    function getCsrfToken() {
        return readMeta("_csrf") || readHidden("csrfToken");
    }

    function getCsrfHeaderName() {
        return readMeta("_csrf_header") || readHidden("csrfHeader") || "X-CSRF-TOKEN";
    }

    function normalizeHeaders(headers) {
        if (!headers) return {};
        if (headers instanceof Headers) {
            const obj = {};
            headers.forEach((v, k) => {
                obj[k] = v;
            });
            return obj;
        }
        return {...headers};
    }

    async function csrfFetch(url, options) {
        const opts = options ? {...options} : {};
        const headers = normalizeHeaders(opts.headers);

        const token = getCsrfToken();
        const headerName = getCsrfHeaderName();
        if (token && headerName && !headers[headerName]) {
            headers[headerName] = token;
        }

        opts.headers = headers;
        return fetch(url, opts);
    }

    window.csrfFetch = csrfFetch;
    window.getCsrfToken = getCsrfToken;
    window.getCsrfHeaderName = getCsrfHeaderName;
})();