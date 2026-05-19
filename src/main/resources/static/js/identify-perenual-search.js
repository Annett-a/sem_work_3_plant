(() => {
    "use strict";

    const $ = (id) => document.getElementById(id);

    function show(el) {
        if (el) el.hidden = false;
    }

    function hide(el) {
        if (el) el.hidden = true;
    }

    function setText(el, text) {
        if (el) el.textContent = text ?? "";
    }

    function clear(el) {
        if (!el) return;
        while (el.firstChild) el.removeChild(el.firstChild);
    }

    function safeText(v) {
        return (v ?? "").toString().trim();
    }

    function joinList(list) {
        return Array.isArray(list)
            ? list.map((x) => safeText(x)).filter(Boolean).join(", ")
            : "";
    }

    function wateringLabel(data) {
        const labels = [];
        const watering = safeText(data.watering);
        const min = Number.isInteger(data.wateringMinDays) ? data.wateringMinDays : null;
        const max = Number.isInteger(data.wateringMaxDays) ? data.wateringMaxDays : null;

        if (watering) labels.push(watering);
        if (min != null && max != null) {
            labels.push(min === max ? `${min} дн.` : `${min}–${max} дн.`);
        }
        return labels.join(" • ");
    }

    function makeMetaRow(label, value) {
        if (!value) return null;

        const row = document.createElement("div");
        row.className = "muted u-mt-6";

        const strong = document.createElement("strong");
        strong.textContent = `${label}: `;
        row.appendChild(strong);

        const span = document.createElement("span");
        span.textContent = value;
        row.appendChild(span);

        return row;
    }

    function buildCard(data) {
        const target = $("perenualSearchCard");
        clear(target);

        const wrap = document.createElement("div");
        wrap.className = "perenual-preview";

        if (data.imageUrl) {
            const img = document.createElement("img");
            img.src = data.imageUrl;
            img.alt = data.name || "plant";
            img.className = "perenual-preview-img";
            wrap.appendChild(img);
        }

        const info = document.createElement("div");

        const title = document.createElement("div");
        title.className = "perenual-preview-title";
        title.textContent = data.name || data.scientificName || "Растение";
        info.appendChild(title);

        const sci = safeText(data.scientificName);
        if (sci) {
            const sciRow = document.createElement("div");
            sciRow.className = "muted u-mt-4";
            sciRow.textContent = sci;
            info.appendChild(sciRow);
        }

        const names = joinList(data.scientificNames);
        if (names && names !== sci) {
            const row = makeMetaRow("Другие названия", names);
            if (row) info.appendChild(row);
        }

        const sunlight = joinList(data.sunlight);
        const watering = wateringLabel(data);
        const careLevel = safeText(data.careLevel);
        const cycle = safeText(data.cycle);

        [
            makeMetaRow("Свет", sunlight),
            makeMetaRow("Полив", watering),
            makeMetaRow("Сложность ухода", careLevel),
            makeMetaRow("Цикл", cycle),
        ].forEach((row) => {
            if (row) info.appendChild(row);
        });

        const description = safeText(data.description);
        if (description) {
            const desc = document.createElement("div");
            desc.className = "u-mt-10";
            desc.textContent = description;
            info.appendChild(desc);
        }

        const actions = document.createElement("div");
        actions.className = "u-mt-12 u-flex u-gap-10 u-flex-wrap";

        if (data.alreadyImported && data.localSpeciesId) {
            const badge = document.createElement("span");
            badge.className = "badge";
            badge.textContent = "Уже в локальном каталоге";
            actions.appendChild(badge);

            const link = document.createElement("a");
            link.className = "btn";
            link.href = `/app/species/${data.localSpeciesId}`;
            link.textContent = "Открыть карточку вида";
            actions.appendChild(link);
        } else {
            const badge = document.createElement("span");
            badge.className = "badge";
            badge.textContent = "Карточка найдена в справочнике";
            actions.appendChild(badge);
        }

        info.appendChild(actions);
        wrap.appendChild(info);
        target.appendChild(wrap);
    }

    async function runSearch() {
        const input = $("perenualSearchQuery");
        const status = $("perenualSearchStatus");
        const err = $("perenualSearchError");
        const wrap = $("perenualSearchWrap");
        if (!input || !wrap) return;

        const query = safeText(input.value);
        show(wrap);
        hide(err);
        setText(err, "");
        clear($("perenualSearchCard"));

        if (!query) {
            setText(status, "Введи название растения для поиска.");
            return;
        }

        setText(status, "Ищу карточку растения...");

        const doFetch = (window.csrfFetch && typeof window.csrfFetch === "function")
            ? window.csrfFetch
            : fetch;

        try {
            const resp = await doFetch(`/api/perenual/card?query=${encodeURIComponent(query)}`, {
                method: "GET",
                headers: {
                    "Accept": "application/json",
                    "X-Requested-With": "XMLHttpRequest",
                },
            });

            const json = await resp.json().catch(() => null);

            if (!resp.ok) {
                const msg = json && json.message
                    ? json.message
                    : "Ошибка запроса";
                setText(status, "");
                setText(err, msg);
                show(err);
                return;
            }

            setText(status, "");
            buildCard(json);
        } catch (e) {
            setText(status, "");
            setText(err, "Ошибка сети");
            show(err);
        }
    }

    function bindPrefillButtons() {
        document.querySelectorAll(".js-fill-perenual-search").forEach((btn) => {
            if (btn.dataset.bound === "1") return;
            btn.dataset.bound = "1";
            btn.addEventListener("click", () => {
                const input = $("perenualSearchQuery");
                if (!input) return;
                input.value = btn.getAttribute("data-query") || "";
                input.focus();
            });
        });
    }

    document.addEventListener("DOMContentLoaded", () => {
        bindPrefillButtons();

        const input = $("perenualSearchQuery");
        const button = $("perenualSearchBtn");
        if (!input || !button) return;

        button.addEventListener("click", runSearch);
        input.addEventListener("keydown", (e) => {
            if (e.key === "Enter") {
                e.preventDefault();
                runSearch();
            }
        });

        window.identifyPerenualSearch = {
            setQuery(value) {
                if (!input) return;
                input.value = value ?? "";
                bindPrefillButtons();
            },
        };
    });
})();