function addDynamicRow(containerId) {
    const container = document.getElementById(containerId);
    if (!container) {
        return;
    }
    const templateId = container.dataset.template;
    const template = document.getElementById(templateId);
    if (!template) {
        return;
    }
    const prefix = container.dataset.prefix;
    const index = Number(container.dataset.index || container.querySelectorAll('.dynamic-row').length) || 0;
    const clone = template.content.cloneNode(true);
    clone.querySelectorAll('[data-field]').forEach((input) => {
        const field = input.dataset.field;
        input.name = `${prefix}[${index}].${field}`;
    });
    container.dataset.index = index + 1;
    container.appendChild(clone);
}

function removeRow(button, containerId) {
    const container = document.getElementById(containerId);
    if (!container) {
        return;
    }
    const row = button.closest('.dynamic-row');
    if (row) {
        row.remove();
        reindexRows(container);
    }
}

function reindexRows(container) {
    const prefix = container.dataset.prefix;
    const rows = container.querySelectorAll('.dynamic-row');
    rows.forEach((row, idx) => {
        row.querySelectorAll('[data-field]').forEach((input) => {
            const field = input.dataset.field;
            input.name = `${prefix}[${idx}].${field}`;
        });
    });
    container.dataset.index = rows.length;
}

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.dynamic-list').forEach(reindexRows);
});

window.addDynamicRow = addDynamicRow;
window.removeRow = removeRow;

