(() => {
    "use strict";

    const $ = (id) => document.getElementById(id);
    const previewCache = new Map();

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

    function readCsrf() {
        const token = $("csrfToken") ? $("csrfToken").value : "";
        const header = $("csrfHeader") ? $("csrfHeader").value : "X-CSRF-TOKEN";
        return {token, header};
    }

    function setHiddenIds(perenualId, localSpeciesId) {
        const perenualInput = $("perenualId");
        const speciesInput = $("speciesId");

        if (perenualInput) {
            perenualInput.value = perenualId && Number(perenualId) > 0 ? String(perenualId) : "";
        }

        if (speciesInput) {
            speciesInput.value = localSpeciesId && Number(localSpeciesId) > 0 ? String(localSpeciesId) : "";
        }
    }

    function setNickname(value) {
        const nicknameInput = $("nickname");
        if (!nicknameInput) return;
        nicknameInput.value = value ? String(value) : "";
    }

    async function doFetch(url, options) {
        if (window.csrfFetch && typeof window.csrfFetch === "function") {
            return window.csrfFetch(url, options);
        }

        const opts = options ? {...options} : {};
        const headers = {...(opts.headers || {})};
        const {token, header} = readCsrf();
        if (token && header && !headers[header]) headers[header] = token;
        opts.headers = headers;
        return fetch(url, opts);
    }

    function buildPreviewCard(data) {
        const card = $("perenualPreviewCard");
        clear(card);

        setHiddenIds(
            data.perenualId,
            data.alreadyImported ? data.localSpeciesId : null
        );

        const wrap = document.createElement("div");
        wrap.className = "perenual-preview";

        if (data.imageUrl) {
            const img = document.createElement("img");
            img.src = data.imageUrl;
            img.alt = data.name || "preview";
            img.className = "perenual-preview-img";
            wrap.appendChild(img);
        }

        const info = document.createElement("div");

        const title = document.createElement("div");
        title.className = "perenual-preview-title";
        title.textContent = data.name || "(без названия)";
        info.appendChild(title);

        if (data.scientificName) {
            const sci = document.createElement("div");
            sci.className = "muted u-mt-4";
            sci.textContent = `Scientific: ${data.scientificName}`;
            info.appendChild(sci);
        }

        const meta = document.createElement("div");
        meta.className = "muted u-mt-6";
        meta.textContent = `ID вида: ${data.perenualId ?? 0}`;
        info.appendChild(meta);

        const actions = document.createElement("div");
        actions.className = "u-mt-10 u-flex u-gap-10 u-flex-wrap";

        if (data.alreadyImported && data.localSpeciesId) {
            const ok = document.createElement("span");
            ok.className = "badge";
            ok.textContent = "Уже в каталоге";
            actions.appendChild(ok);

            const link = document.createElement("a");
            link.className = "btn";
            link.href = `/app/species/${data.localSpeciesId}`;
            link.textContent = "Открыть карточку вида";
            actions.appendChild(link);
        } else {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = "btn btn-primary";
            btn.textContent = "Импортировать";
            btn.dataset.perenualId = String(data.perenualId);
            btn.id = "btnPerenualImport";

            btn.addEventListener("click", async () => {
                btn.disabled = true;
                setText($("perenualPreviewStatus"), "Импортирую...");
                hide($("perenualPreviewError"));
                setText($("perenualPreviewError"), "");

                try {
                    const resp = await doFetch("/api/perenual/import", {
                        method: "POST",
                        headers: {
                            "Accept": "application/json",
                            "Content-Type": "application/json",
                            "X-Requested-With": "XMLHttpRequest",
                        },
                        body: JSON.stringify({perenualId: Number(btn.dataset.perenualId)}),
                    });

                    const json = await resp.json().catch(() => null);

                    if (!resp.ok) {
                        const msg = json && json.message
                            ? json.message
                            : "Ошибка импорта";
                        setText($("perenualPreviewStatus"), "");
                        setText($("perenualPreviewError"), msg);
                        show($("perenualPreviewError"));
                        btn.disabled = false;
                        return;
                    }

                    setText($("perenualPreviewStatus"), "Импортировано.");

                    const updated = {
                        ...data,
                        alreadyImported: true,
                        localSpeciesId: json.localSpeciesId,
                    };

                    previewCache.set((data.scientificName || "").trim().toLowerCase(), updated);
                    buildPreviewCard(updated);

                } catch (e) {
                    setText($("perenualPreviewStatus"), "");
                    setText($("perenualPreviewError"), "Ошибка сети");
                    show($("perenualPreviewError"));
                    btn.disabled = false;
                }
            });

            actions.appendChild(btn);
        }

        info.appendChild(actions);
        wrap.appendChild(info);
        card.appendChild(wrap);
    }

    async function loadPreview(scientificName) {
        const wrap = $("perenualPreviewWrap");
        const status = $("perenualPreviewStatus");
        const err = $("perenualPreviewError");

        const key = (scientificName || "").trim().toLowerCase();
        if (!key) {
            setHiddenIds(null, null);
            return;
        }

        show(wrap);
        hide(err);
        setText(err, "");
        clear($("perenualPreviewCard"));

        if (previewCache.has(key)) {
            setText(status, "");
            buildPreviewCard(previewCache.get(key));
            return;
        }

        setText(status, "Ищу карточку вида...");

        try {
            const resp = await doFetch(`/api/perenual/preview?scientificName=${encodeURIComponent(scientificName)}`, {
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
                setHiddenIds(null, null);
                return;
            }

            previewCache.set(key, json);
            setText(status, "");
            buildPreviewCard(json);

        } catch (e) {
            setText(status, "");
            setText(err, "Ошибка сети");
            show(err);
            setHiddenIds(null, null);
        }
    }

    document.addEventListener("DOMContentLoaded", () => {
        const radios = document.querySelectorAll("input[type='radio'][name$='selectedScientificName']");
        const selectedInfo = $("createPhotoSelected");
        if (!radios || radios.length === 0) return;

        function updateSelectedText(value) {
            setText(selectedInfo, value ? `Выбрано: ${value}` : "");
        }

        radios.forEach((r) => {
            r.addEventListener("change", () => {
                setHiddenIds(null, null);

                if (r.checked) {
                    updateSelectedText(r.value);
                    setNickname(r.value);
                    loadPreview(r.value);
                }
            });
        });

        const preselected = Array.from(radios).find((r) => r.checked);
        if (preselected) {
            updateSelectedText(preselected.value);
            setNickname(preselected.value);
            loadPreview(preselected.value);
        } else {
            updateSelectedText("");
        }
    });
})();