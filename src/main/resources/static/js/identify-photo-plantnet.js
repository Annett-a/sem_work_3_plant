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
        const buttons = document.querySelectorAll(".js-identify-photo");
        if (!buttons || buttons.length === 0) return;

        const status = $("plantnetStatus");
        const err = $("plantnetError");

        buttons.forEach((b) => {
            b.addEventListener("click", async () => {
                hide(err);
                setText(err, "");
                setText($("plantnetSelected"), "");
                setText(status, "");

                const photoId = b.getAttribute("data-photo-id");
                if (!photoId) return;

                b.disabled = true;
                setText(status, `Распознаю фото #${photoId}...`);

                try {
                    const doFetch = (window.csrfFetch && typeof window.csrfFetch === "function")
                        ? window.csrfFetch
                        : fetch;

                    const resp = await doFetch(`/api/identify/plantnet/photo/${photoId}`, {
                        method: "POST",
                        headers: {
                            "Accept": "application/json",
                            "X-Requested-With": "XMLHttpRequest",
                        },
                    });

                    const data = await resp.json().catch(() => null);

                    if (!resp.ok) {
                        const msg = data && (data.message || data.code)
                            ? `${data.code ?? "ERROR"}: ${data.message ?? ""}`
                            : "Ошибка распознавания";
                        setText(status, "");
                        setText(err, msg);
                        show(err);
                        return;
                    }

                    setText(status, "Готово. Выбери кандидата.");
                    renderCandidates(data);
                } catch (e) {
                    setText(status, "");
                    setText(err, "Ошибка сети");
                    show(err);
                } finally {
                    b.disabled = false;
                }
            });
        });
    });
})();