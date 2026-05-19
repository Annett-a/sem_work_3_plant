(() => {
    "use strict";

    const $ = (id) => document.getElementById(id);

    function show(el) {
        if (el) el.hidden = false;
    }

    function hide(el) {
        if (el) el.hidden = true;
    }

    function setText(el, t) {
        if (el) el.textContent = t ?? "";
    }

    function clear(el) {
        if (!el) return;
        while (el.firstChild) el.removeChild(el.firstChild);
    }

    function pct(score) {
        const v = typeof score === "number" ? score : 0;
        return `${Math.round(v * 100)}%`;
    }

    function renderCandidates(list) {
        const box = $("plantnetCandidates");
        clear(box);

        const arr = Array.isArray(list) ? list : [];
        if (arr.length === 0) {
            const empty = document.createElement("div");
            empty.className = "muted";
            empty.textContent = "Ничего не найдено";
            box.appendChild(empty);
            return;
        }

        arr.forEach((c) => {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = "candidate-item";

            const title = document.createElement("div");
            title.className = "u-font-700";
            title.textContent = `${c.name ?? "(unknown)"} — ${pct(c.score)}`;

            btn.appendChild(title);

            if (Array.isArray(c.commonNames) && c.commonNames.length > 0) {
                const cn = document.createElement("div");
                cn.className = "muted u-mt-4";
                cn.textContent = c.commonNames.join(", ");
                btn.appendChild(cn);
            }

            btn.addEventListener("click", () => {
                setText($("plantnetSelected"), `Выбрано: ${c.name ?? "(unknown)"} (${pct(c.score)})`);
                if (window.identifyPerenualSearch && typeof window.identifyPerenualSearch.setQuery === "function") {
                    window.identifyPerenualSearch.setQuery(c.name ?? "");
                }
            });

            box.appendChild(btn);
        });
    }

    document.addEventListener("DOMContentLoaded", () => {
        const input = $("plantnetFile");
        const btn = $("plantnetBtn");
        const status = $("plantnetStatus");
        const err = $("plantnetError");

        if (!input || !btn) return;

        btn.addEventListener("click", async () => {
            hide(err);
            setText(err, "");
            setText($("plantnetSelected"), "");
            setText(status, "");

            const file = input.files && input.files[0] ? input.files[0] : null;
            if (!file) {
                setText(err, "Выбери JPG/PNG файл");
                show(err);
                return;
            }

            btn.disabled = true;
            setText(status, "Распознаю...");

            const form = new FormData();
            form.append("file", file);

            try {
                const doFetch = (window.csrfFetch && typeof window.csrfFetch === "function")
                    ? window.csrfFetch
                    : fetch;

                const resp = await doFetch("/api/identify/plantnet", {
                    method: "POST",
                    headers: {
                        "Accept": "application/json",
                        "X-Requested-With": "XMLHttpRequest",
                    },
                    body: form,
                });

                const data = await resp.json().catch(() => null);

                if (!resp.ok) {
                    const msg = data && (data.message || data.code)
                        ? `${data.code ?? "ERROR"}: ${data.message ?? ""}`
                        : "Ошибка распознавания";
                    setText(status, "");
                    setText(err, msg);
                    show(err);
                    btn.disabled = false;
                    return;
                }

                setText(status, "Готово. Выбери кандидата.");
                renderCandidates(data);
            } catch (e) {
                setText(status, "");
                setText(err, "Ошибка сети");
                show(err);
            } finally {
                btn.disabled = false;
            }
        });
    });
})();