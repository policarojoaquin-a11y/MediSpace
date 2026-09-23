// ==================== OBRAS SOCIALES MODULE ====================
let obrasSocialesData = [];
let editingObraSocialId = null;

async function loadObrasSociales() {
  const tbody = document.getElementById('tabla-obras-sociales');
  tbody.innerHTML = `<tr><td colspan="7"><div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div></td></tr>`;
  try {
    const params = {
      nombre: document.getElementById('search-obras-sociales')?.value.trim() || '',
      requiereBono: document.getElementById('filtro-obras-sociales-bono')?.value || '',
      incluirInactivas: document.getElementById('filtro-obras-sociales-inactivas')?.checked || false,
    };
    obrasSocialesData = await Api.getObrasSociales(params);
    renderObrasSociales(obrasSocialesData);
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="7"><div class="alert alert-error">${e.message}</div></td></tr>`;
  }
}

function renderObrasSociales(data) {
  const tbody = document.getElementById('tabla-obras-sociales');
  if (!data || data.length === 0) {
    tbody.innerHTML = `<tr><td colspan="7"><div class="empty-state"><svg width="40" height="40" viewBox="0 0 24 24" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" stroke-width="2"/><polyline points="14 2 14 8 20 8" stroke="currentColor" stroke-width="2"/></svg><h3>Sin obras sociales</h3><p>Registrá la primera obra social.</p></div></td></tr>`;
    return;
  }
  tbody.innerHTML = data.map(os => `
    <tr>
      <td><strong>${os.nombre}</strong></td>
      <td>${os.codigoSigla || '—'}</td>
      <td>${os.plan || '—'}</td>
      <td>${os.requiereBono ? 'Sí' : 'No'}</td>
      <td>${os.cantidadMedicosAsociados ?? 0}</td>
      <td><span class="badge ${os.visible ? 'badge-activo' : 'badge-inactivo'}">${os.visible ? 'Activa' : 'Inactiva'}</span></td>
      <td>
        <div class="table-actions">
          <button class="btn-icon" title="Editar" onclick="editarObraSocial(${os.idObraSocial})">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2"/></svg>
          </button>
          ${os.visible
            ? `<button class="btn-icon" title="Dar de baja" onclick="bajaObraSocial(${os.idObraSocial})" style="color:var(--rojo)">
                 <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><polyline points="3 6 5 6 21 6" stroke="currentColor" stroke-width="2"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" stroke="currentColor" stroke-width="2"/></svg>
               </button>`
            : `<button class="btn-icon" title="Reactivar" onclick="reactivarObraSocialAccion(${os.idObraSocial})" style="color:var(--verde)">
                 <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><polyline points="23 4 23 10 17 10" stroke="currentColor" stroke-width="2"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" stroke="currentColor" stroke-width="2"/></svg>
               </button>`}
        </div>
      </td>
    </tr>`).join('');
}

// Filtros: recargan desde el backend (el toggle "incluir inactivas" necesita ir al servidor)
document.getElementById('search-obras-sociales')?.addEventListener('input', debounceLoadObrasSociales);
document.getElementById('filtro-obras-sociales-bono')?.addEventListener('change', loadObrasSociales);
document.getElementById('filtro-obras-sociales-inactivas')?.addEventListener('change', loadObrasSociales);

let obrasSocialesDebounceTimer = null;
function debounceLoadObrasSociales() {
  clearTimeout(obrasSocialesDebounceTimer);
  obrasSocialesDebounceTimer = setTimeout(loadObrasSociales, 300);
}

function limpiarFormObraSocial() {
  document.getElementById('os-nombre').value = '';
  document.getElementById('os-codigo-sigla').value = '';
  document.getElementById('os-plan').value = '';
  document.getElementById('os-requiere-bono').checked = false;
  document.getElementById('os-observaciones').value = '';
}

// Open modal new
document.getElementById('btn-nueva-obra-social')?.addEventListener('click', () => {
  editingObraSocialId = null;
  document.getElementById('modal-obra-social-title').textContent = 'Nueva Obra Social';
  limpiarFormObraSocial();
  openModal('modal-obra-social');
});

function editarObraSocial(id) {
  const os = obrasSocialesData.find(x => x.idObraSocial === id);
  if (!os) return;
  editingObraSocialId = id;
  document.getElementById('modal-obra-social-title').textContent = 'Editar Obra Social';
  document.getElementById('os-nombre').value = os.nombre || '';
  document.getElementById('os-codigo-sigla').value = os.codigoSigla || '';
  document.getElementById('os-plan').value = os.plan || '';
  document.getElementById('os-requiere-bono').checked = !!os.requiereBono;
  document.getElementById('os-observaciones').value = os.observaciones || '';
  openModal('modal-obra-social');
}

async function guardarObraSocial() {
  const btn = document.getElementById('btn-guardar-obra-social');
  const payload = {
    nombre:         document.getElementById('os-nombre').value.trim(),
    codigoSigla:    document.getElementById('os-codigo-sigla').value.trim(),
    plan:           document.getElementById('os-plan').value.trim(),
    requiereBono:   document.getElementById('os-requiere-bono').checked,
    observaciones:  document.getElementById('os-observaciones').value.trim(),
  };
  if (!payload.nombre) {
    Toast.error('Ingresá un nombre para la obra social.');
    return;
  }
  btn.disabled = true;
  try {
    if (editingObraSocialId) {
      await Api.updateObraSocial(editingObraSocialId, payload);
      Toast.success('Obra social actualizada.');
    } else {
      await Api.createObraSocial(payload);
      Toast.success('Obra social agregada.');
    }
    closeModal('modal-obra-social');
    loadObrasSociales();
  } catch (e) {
    Toast.error(e.message);
  } finally {
    btn.disabled = false;
  }
}

async function bajaObraSocial(id) {
  if (!confirm('¿Dar de baja esta obra social?')) return;
  try {
    await Api.deleteObraSocial(id);
    Toast.success('Obra social dada de baja.');
    loadObrasSociales();
  } catch (e) {
    Toast.error(e.message);
  }
}

async function reactivarObraSocialAccion(id) {
  try {
    await Api.reactivarObraSocial(id);
    Toast.success('Obra social reactivada.');
    loadObrasSociales();
  } catch (e) {
    Toast.error(e.message);
  }
}
