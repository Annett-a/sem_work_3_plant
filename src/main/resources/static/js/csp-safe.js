document.addEventListener('DOMContentLoaded', () => {
    const tagSelect = document.getElementById('tagSelect');
    const tagsHidden = document.getElementById('tags');
    const catalogForm = tagSelect && tagsHidden ? tagSelect.closest('form') : null;

    if (catalogForm) {
        catalogForm.addEventListener('submit', () => {
            const ids = Array.from(tagSelect.selectedOptions)
                .map(option => option.value)
                .filter(value => value && value.trim().length > 0);

            tagsHidden.value = ids.join(',');
        });
    }

    document.querySelectorAll('.plant-photos-delete-form').forEach(form => {
        form.addEventListener('submit', event => {
            const ok = window.confirm('Удалить фото?');
            if (!ok) {
                event.preventDefault();
            }
        });
    });

    document.querySelectorAll('img[data-fallback-src]').forEach(img => {
        img.addEventListener('error', function () {
            const fallbackSrc = this.dataset.fallbackSrc;
            if (!fallbackSrc) {
                return;
            }
            if (this.dataset.fallbackApplied === 'true') {
                return;
            }
            this.dataset.fallbackApplied = 'true';
            this.src = fallbackSrc;
        });
    });
});