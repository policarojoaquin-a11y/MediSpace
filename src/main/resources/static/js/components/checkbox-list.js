/**
 * Renders a scrollable list of checkboxes from an array of items.
 * Returns a handle with getSelectedIds() so callers can read the current selection.
 */
function renderCheckboxList(containerEl, items, { idKey, labelKey, selectedIds = [] } = {}) {
  const selected = new Set((selectedIds || []).map(String));
  containerEl.innerHTML = '';
  containerEl.classList.add('checkbox-list');

  (items || []).forEach(item => {
    const id = item[idKey];
    const label = document.createElement('label');
    label.className = 'checkbox-list-item';

    const input = document.createElement('input');
    input.type = 'checkbox';
    input.value = id;
    input.checked = selected.has(String(id));

    label.appendChild(input);
    label.appendChild(document.createTextNode(' ' + item[labelKey]));
    containerEl.appendChild(label);
  });

  return {
    getSelectedIds() {
      return Array.from(containerEl.querySelectorAll('input[type="checkbox"]:checked'))
        .map(input => parseInt(input.value, 10));
    },
    setSelectedIds(ids) {
      const set = new Set((ids || []).map(String));
      containerEl.querySelectorAll('input[type="checkbox"]').forEach(input => {
        input.checked = set.has(input.value);
      });
    }
  };
}
