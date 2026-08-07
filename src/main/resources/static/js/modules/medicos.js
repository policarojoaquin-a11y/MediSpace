// ==================== MÉDICOS MODULE ====================
let medicosData = [];
let editingMedicoId = null;

async function loadMedicos() {
  const tbody = document.getElementById('tabla-medicos');
  tbody.innerHTML = `<tr><td colspan="6"><div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div></td></tr>`;
  try {
    medicosData = await Api.getMedicos();
    renderMedicos(medicosData);
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="6"><div class="alert alert-error">${e.message}</div></td></tr>`;
  }
}

function renderMedicos(data) {
  const tbody = document.getElementById('tabla-medicos');
  if (!data || data.length === 0) {
    tbody.innerHTML = `<tr><td colspan="6"><div class="empty-state"><svg width="40" height="40" viewBox="0 0 24 24" fill="none"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="7" r="4" stroke="currentColor" stroke-width="2"/></svg><h3>Sin médicos</h3><p>Registrá el primer médico.</p></div></td></tr>`;
    return;
  }
  tbody.innerHTML = data.map(m => `
    <tr>
      <td><strong>Dr/a. ${m.apellido}, ${m.nombre}</strong></td>
      <td>${m.matricula || '—'}</td>
      <td>${m.nombreEspecialidad || '—'}</td>
      <td>$${Number(m.importeConsulta || 0).toLocaleString('es-AR', {minimumFractionDigits:2})}</td>
      <td><span class="badge badge-activo">Activo</span></td>
      <td>
        <div class="table-actions">
          <button class="btn-icon" title="Editar" onclick="editarMedico(${m.idMedico})">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2"/></svg>
          </button>
          <button class="btn-icon" title="Eliminar" onclick="bajaMedico(${m.idMedico})" style="color:var(--rojo)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><polyline points="3 6 5 6 21 6" stroke="currentColor" stroke-width="2"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" stroke="currentColor" stroke-width="2"/></svg>
          </button>
        </div>
      </td>
    </tr>`).join('');
}

// Search
document.getElementById('search-medicos')?.addEventListener('input', (e) => {
  const q = e.target.value.toLowerCase();
  renderMedicos(medicosData.filter(m =>
    `${m.nombre} ${m.apellido} ${m.matricula} ${m.nombreEspecialidad}`.toLowerCase().includes(q)
  ));
});

let especialidadesCache = [];
let medicoObrasSocialesCache = [];
let obrasSocialesCheckboxList = null;
let medicoPrestacionesRows = [];
let medicoPrestacionesEliminadas = [];

async function populateEspecialidadSelect(selectedId) {
  const sel = document.getElementById('med-especialidad');
  if (!especialidadesCache.length) {
    especialidadesCache = await Api.getEspecialidades().catch(() => []);
  }
  sel.innerHTML = '<option value="">Seleccioná una especialidad…</option>';
  especialidadesCache.forEach(e => {
    const opt = document.createElement('option');
    opt.value = e.idEspecialidad;
    opt.textContent = e.nombre;
    sel.appendChild(opt);
  });
  const optNueva = document.createElement('option');
  optNueva.value = '__new__';
  optNueva.textContent = '+ Nueva especialidad…';
  sel.appendChild(optNueva);
  sel.value = selectedId || '';
  document.getElementById('especialidad-nueva-inline').classList.add('hidden');
}

document.getElementById('med-especialidad')?.addEventListener('change', (e) => {
  const inline = document.getElementById('especialidad-nueva-inline');
  if (e.target.value === '__new__') {
    inline.classList.remove('hidden');
    document.getElementById('especialidad-nueva-nombre').value = '';
    document.getElementById('especialidad-nueva-nombre').focus();
  } else {
    inline.classList.add('hidden');
  }
});

async function confirmarNuevaEspecialidad() {
  const nombre = document.getElementById('especialidad-nueva-nombre').value.trim();
  if (!nombre) {
    Toast.error('Ingresá un nombre para la nueva especialidad.');
    return;
  }
  try {
    const nueva = await Api.createEspecialidad(nombre);
    especialidadesCache.push(nueva);
    await populateEspecialidadSelect(nueva.idEspecialidad);
    Toast.success('Especialidad agregada.');
  } catch (e) {
    Toast.error(e.message);
  }
}

async function populateObrasSocialesCheckboxes(selectedIds) {
  if (!medicoObrasSocialesCache.length) {
    medicoObrasSocialesCache = await Api.getObrasSociales().catch(() => []);
  }
  const container = document.getElementById('medico-obrassociales-list');
  obrasSocialesCheckboxList = renderCheckboxList(container, medicoObrasSocialesCache, {
    idKey: 'idObraSocial',
    labelKey: 'nombre',
    selectedIds: selectedIds || []
  });
}

function renderPrestacionesTable() {
  const tbody = document.getElementById('medico-prestaciones-tbody');
  tbody.innerHTML = medicoPrestacionesRows.map((row, idx) => `
    <tr>
      <td><input type="text" value="${row.nombrePrestacion || ''}" onchange="medicoPrestacionesRows[${idx}].nombrePrestacion = this.value" placeholder="Nombre de la prestación" /></td>
      <td><input type="number" min="0" value="${row.duracionEstimadaMin || ''}" onchange="medicoPrestacionesRows[${idx}].duracionEstimadaMin = parseInt(this.value) || null" /></td>
      <td><input type="number" min="0" step="0.01" value="${row.importeParticular || ''}" onchange="medicoPrestacionesRows[${idx}].importeParticular = parseFloat(this.value) || null" /></td>
      <td><input type="text" value="${row.tipo || ''}" onchange="medicoPrestacionesRows[${idx}].tipo = this.value" /></td>
      <td><button type="button" class="btn-icon" title="Quitar" onclick="quitarFilaPrestacion(${idx})">✕</button></td>
    </tr>`).join('') || '<tr><td colspan="5" style="color:var(--gris-medio)">Sin prestaciones agregadas.</td></tr>';
}

function agregarFilaPrestacion() {
  medicoPrestacionesRows.push({ idMedicoPrestacion: null, idPrestacion: null, nombrePrestacion: '', duracionEstimadaMin: null, importeParticular: null, tipo: '' });
  renderPrestacionesTable();
}

function quitarFilaPrestacion(idx) {
  const row = medicoPrestacionesRows[idx];
  if (row.idMedicoPrestacion) {
    medicoPrestacionesEliminadas.push(row.idMedicoPrestacion);
  }
  medicoPrestacionesRows.splice(idx, 1);
  renderPrestacionesTable();
}

// Open modal new
document.getElementById('btn-nuevo-medico')?.addEventListener('click', async () => {
  editingMedicoId = null;
  document.getElementById('modal-medico-title').textContent = 'Nuevo Médico';
  ['med-nombre','med-apellido','med-matricula','med-email','med-telefono'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.value = '';
  });
  document.getElementById('med-importe').value = '';
  document.getElementById('med-inicio').value = '';
  medicoPrestacionesRows = [];
  medicoPrestacionesEliminadas = [];
  renderPrestacionesTable();
  await Promise.all([populateEspecialidadSelect(), populateObrasSocialesCheckboxes([])]);
  openModal('modal-medico');
});

async function editarMedico(id) {
  const m = medicosData.find(x => x.idMedico === id);
  if (!m) return;
  editingMedicoId = id;
  document.getElementById('modal-medico-title').textContent = 'Editar Médico';
  document.getElementById('med-nombre').value      = m.nombre || '';
  document.getElementById('med-apellido').value    = m.apellido || '';
  document.getElementById('med-matricula').value   = m.matricula || '';
  document.getElementById('med-importe').value     = m.importeConsulta || '';
  document.getElementById('med-inicio').value      = m.fechaInicioActividad || '';
  document.getElementById('med-email').value       = m.email || '';
  document.getElementById('med-telefono').value    = m.telefono || '';
  medicoPrestacionesRows = (m.prestaciones || []).map(p => ({ ...p }));
  medicoPrestacionesEliminadas = [];
  renderPrestacionesTable();
  await Promise.all([
    populateEspecialidadSelect(m.idEspecialidad),
    populateObrasSocialesCheckboxes(m.idsObrasSociales || [])
  ]);
  openModal('modal-medico');
}

async function guardarMedico() {
  const btn = document.getElementById('btn-guardar-medico');
  const payload = {
    nombre:             document.getElementById('med-nombre').value.trim(),
    apellido:           document.getElementById('med-apellido').value.trim(),
    matricula:          document.getElementById('med-matricula').value.trim(),
    idEspecialidad:     parseInt(document.getElementById('med-especialidad').value) || null,
    importeConsulta:    parseFloat(document.getElementById('med-importe').value) || 0,
    fechaInicioActividad: document.getElementById('med-inicio').value,
    email:              document.getElementById('med-email').value.trim(),
    telefono:           document.getElementById('med-telefono').value.trim(),
    idsObrasSociales:   obrasSocialesCheckboxList ? obrasSocialesCheckboxList.getSelectedIds() : [],
  };
  if (!payload.nombre || !payload.apellido || !payload.matricula || !payload.email || !payload.idEspecialidad || !payload.fechaInicioActividad) {
    Toast.error('Completá los campos obligatorios (nombre, apellido, matrícula, especialidad, inicio de actividad y email).');
    return;
  }
  btn.disabled = true;
  try {
    if (editingMedicoId) {
      await Api.updateMedico(editingMedicoId, payload);
      // Prestaciones: alta/edición/baja lógica vía endpoints dedicados
      for (const idEliminado of medicoPrestacionesEliminadas) {
        await Api.deleteMedicoPrestacion(idEliminado);
      }
      for (const row of medicoPrestacionesRows) {
        if (row.idMedicoPrestacion) {
          await Api.updateMedicoPrestacion(row.idMedicoPrestacion, row);
        } else {
          await Api.addMedicoPrestacion(editingMedicoId, row);
        }
      }
      Toast.success('Médico actualizado.');
    } else {
      payload.prestaciones = medicoPrestacionesRows;
      await Api.createMedico(payload);
      Toast.success('Médico registrado. Se creó su usuario de acceso.');
    }
    closeModal('modal-medico');
    loadMedicos();
  } catch (e) {
    Toast.error(e.message);
  } finally {
    btn.disabled = false;
  }
}

async function bajaMedico(id) {
  if (!confirm('¿Dar de baja al médico? Se validará que no tenga turnos futuros pendientes.')) return;
  try {
    await Api.deleteMedico(id);
    Toast.success('Médico dado de baja.');
    loadMedicos();
  } catch (e) {
    Toast.error(e.message);
  }
}

// ---- Mis Prestaciones (Médico, solo lectura) ----
async function loadMisPrestaciones() {
  const container = document.getElementById('mis-prestaciones-container');
  container.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div>`;
  try {
    const prestaciones = await Api.getMisPrestaciones();
    if (!prestaciones || prestaciones.length === 0) {
      container.innerHTML = `<div class="empty-state"><h3>Sin prestaciones</h3><p>Todavía no tenés prestaciones asociadas.</p></div>`;
      return;
    }
    container.innerHTML = `
      <table class="table">
        <thead><tr><th>Prestación</th><th>Duración (min)</th><th>Importe Particular</th><th>Tipo</th></tr></thead>
        <tbody>
          ${prestaciones.map(p => `
            <tr>
              <td>${p.nombrePrestacion || '—'}</td>
              <td>${p.duracionEstimadaMin ?? '—'}</td>
              <td>${p.importeParticular != null ? '$' + Number(p.importeParticular).toLocaleString('es-AR', {minimumFractionDigits:2}) : '—'}</td>
              <td>${p.tipo || '—'}</td>
            </tr>`).join('')}
        </tbody>
      </table>`;
  } catch (e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

// ---- Mis Datos (Médico) ----
async function loadMisDatos() {
  const container = document.getElementById('mis-datos-container');
  container.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div>`;
  const rol = getRol();
  try {
    if (rol === 'MEDICO') {
      const m = await Api.getMisDatosMedico();
      container.innerHTML = `
        <div class="card">
          <div class="card-header"><span class="card-title">Dr/a. ${m.apellido}, ${m.nombre}</span></div>
          <div class="form-row">
            <div class="form-group"><label>Matrícula</label><p>${m.matricula || '—'}</p></div>
            <div class="form-group"><label>Especialidad</label><p>${m.nombreEspecialidad || '—'}</p></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label>Email</label><p>${m.email || '—'}</p></div>
            <div class="form-group"><label>Importe Consulta</label><p>$${Number(m.importeConsulta || 0).toLocaleString('es-AR', {minimumFractionDigits:2})}</p></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label>Inicio de Actividad</label><p>${m.fechaInicioActividad || '—'}</p></div>
            <div class="form-group"><label>Estado</label><p>${m.estado || '—'}</p></div>
          </div>
          <div class="form-group"><label>Obras Sociales</label><p>${(m.obrasSociales || []).join(', ') || 'Ninguna'}</p></div>
        </div>`;
    } else {
      container.innerHTML = `<div class="empty-state"><h3>No disponible</h3><p>No hay datos personales para este rol.</p></div>`;
    }
  } catch (e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}
