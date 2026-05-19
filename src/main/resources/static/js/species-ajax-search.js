(() => {
    "use strict";

    const DEBOUNCE_MS = 250;
    const MIN_QUERY_LEN = 2;
    const MAX_ITEMS = 10;

    function safeText(s) {
        return (s ?? "").toString();
    }

    function buildLabel(item) {
        const name = safeText(item.name).trim();
        const latin = safeText(item.latinName).trim();
        if (name && latin) return `${name} (${latin})`;
        return name || latin || "(без названия)";
    }

    function clear(node) {
        while (node.firstChild) node.removeChild(node.firstChild);
    }

    document.addEventListener("DOMContentLoaded", () => {
        const input = document.getElementById("speciesSearch");
        const select = document.getElementById("speciesId");
        const box = document.getElementById("speciesCandidates");
        const status = document.getElementById("speciesSearchStatus");

        if (!input || !select || !box) return;

        if (select.value) {
            const opt = select.options[select.selectedIndex];
            if (opt && opt.text) input.value = opt.text;
        }

        let timer = null;
        let abortController = null;

        async function runSearch(q) {
            if (abortController) abortController.abort();
            abortController = new AbortController();

            if (status) status.textContent = "Поиск...";
            clear(box);

            const url = `/api/species?q=${encodeURIComponent(q)}`;

            const doFetch = (window.csrfFetch && typeof window.csrfFetch === "function")
                ? window.csrfFetch
                : fetch;

            let resp;
            try {
                resp = await doFetch(url, {
                    method: "GET",
                    headers: {
                        "Accept": "application/json",
                        "X-Requested-With": "XMLHttpRequest",
                    },
                    signal: abortController.signal,
                });
            } catch (e) {
                if (e && e.name === "AbortError") return;
                if (status) status.textContent = "Ошибка сети";
                return;
            }

            let data = null;
            try {
                data = await resp.json();
            } catch {
            }

            if (!resp.ok) {
                const msg = data && (data.message || data.code)
                    ? `${data.code ?? "ERROR"}: ${data.message ?? ""}`
                    : "Ошибка запроса";
                if (status) status.textContent = msg;
                return;
            }

            const items = Array.isArray(data) ? data.slice(0, MAX_ITEMS) : [];

            if (items.length === 0) {
                if (status) status.textContent = "Ничего не найдено";
                return;
            }

            if (status) status.textContent = "";

            items.forEach((item) => {
                const id = item && item.id != null ? String(item.id) : "";
                const label = buildLabel(item);

                const btn = document.createElement("button");
                btn.type = "button";
                btn.className = "candidate-item";
                btn.dataset.id = id;
                btn.textContent = label;

                btn.addEventListener("click", () => {
                    let opt = Array.from(select.options).find((o) => o.value === id);
                    if (!opt) {
                        opt = document.createElement("option");
                        opt.value = id;
                        opt.text = label;
                        select.appendChild(opt);
                    }

                    select.value = id;
                    input.value = opt.text;
                    clear(box);
                    if (status) status.textContent = "";
                });

                box.appendChild(btn);
            });
        }

        function onInput() {
            const q = safeText(input.value).trim();

            if (timer) clearTimeout(timer);
            if (abortController) abortController.abort();

            if (q.length < MIN_QUERY_LEN) {
                clear(box);
                if (status) status.textContent = q.length === 0 ? "" : `Введите минимум ${MIN_QUERY_LEN} символа`;
                return;
            }

            timer = setTimeout(() => runSearch(q), DEBOUNCE_MS);
        }

        input.addEventListener("input", onInput);
        input.addEventListener("focus", onInput);

        document.addEventListener("click", (e) => {
            if (e.target === input || box.contains(e.target)) return;
            clear(box);
            if (status) status.textContent = "";
        });
    });
})();