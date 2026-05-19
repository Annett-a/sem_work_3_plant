(() => {
    "use strict";

    function $(id) {
        return document.getElementById(id);
    }

    function setText(el, text) {
        if (!el) return;
        el.textContent = text ?? "";
    }

    function show(el) {
        if (!el) return;
        el.hidden = false;
    }

    function hide(el) {
        if (!el) return;
        el.hidden = true;
    }

    function clearChildren(el) {
        if (!el) return;
        while (el.firstChild) el.removeChild(el.firstChild);
    }

    function formatDateRu(isoDate) {
        if (!isoDate) return "—";
        const [y, m, d] = isoDate.split("-");
        if (!y || !m || !d) return isoDate;
        return `${d}.${m}.${y}`;
    }

    function renderTasks(tasks) {
        const block = $("tasksBlock");
        if (!block) return;

        const arr = Array.isArray(tasks) ? tasks : [];
        let list = $("tasksList");
        let empty = $("tasksEmpty");

        if (arr.length === 0) {
            if (list) {
                clearChildren(list);
                list.hidden = true;
            }

            if (!empty) {
                empty = document.createElement("div");
                empty.id = "tasksEmpty";
                empty.className = "muted plant-details-empty";
                empty.textContent = "Пока нет задач.";
                block.appendChild(empty);
            } else {
                empty.hidden = false;
            }

            return;
        }

        if (empty) {
            empty.remove();
        }

        if (!list) {
            list = document.createElement("div");
            list.id = "tasksList";
            list.className = "plant-details-task-list";
            block.appendChild(list);
        } else {
            list.hidden = false;
        }

        clearChildren(list);

        arr.forEach((t) => {
            const row = document.createElement("article");
            row.className = "plant-details-task-row";

            const top = document.createElement("div");
            top.className = "plant-details-task-row__top";

            const type = document.createElement("div");
            type.className = "plant-details-task-row__type";
            type.textContent = t.typeLabel || t.type || "Уход";

            const date = document.createElement("div");
            date.className = "muted plant-details-task-row__date";
            date.textContent = formatDateRu(t.dueDate);

            top.appendChild(type);
            top.appendChild(date);
            row.appendChild(top);
            list.appendChild(row);
        });
    }

    document.addEventListener("DOMContentLoaded", () => {
        const btn = $("btnWaterNow");
        const next = $("nextWateringText");
        const status = $("waterAjaxStatus");
        const error = $("waterAjaxError");

        if (!btn) return;

        btn.addEventListener("click", async () => {
            hide(error);
            setText(error, "");
            setText(status, "Добавляю полив...");

            btn.disabled = true;

            const plantId = btn.dataset.plantId;

            let resp;
            try {
                const doFetch = (window.csrfFetch && typeof window.csrfFetch === "function")
                    ? window.csrfFetch
                    : fetch;

                resp = await doFetch(`/api/plants/${encodeURIComponent(plantId)}/water`, {
                    method: "POST",
                    headers: {
                        "Accept": "application/json",
                        "Content-Type": "application/json",
                        "X-Requested-With": "XMLHttpRequest",
                    },
                    body: JSON.stringify({}),
                });
            } catch (e) {
                setText(status, "");
                setText(error, "Ошибка сети");
                show(error);
                btn.disabled = false;
                return;
            }

            let data = null;
            try {
                data = await resp.json();
            } catch {
            }

            if (!resp.ok) {
                setText(status, "");
                const msg = data && (data.message || data.code)
                    ? `${data.code ?? "ERROR"}: ${data.message ?? ""}`
                    : "Ошибка запроса";
                setText(error, msg);
                show(error);
                btn.disabled = false;
                return;
            }

            if (data && typeof data.nextWateringText === "string") {
                setText(next, data.nextWateringText);
            }
            renderTasks(data ? data.tasks : []);
            setText(status, "Готово: полив добавлен, план обновлён");
            btn.disabled = false;
        });
    });
})();