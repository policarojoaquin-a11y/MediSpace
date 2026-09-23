// ==================== MÉDICOS MODULE ====================
let medicosData = [];
let editingMedicoId = null;

async function loadMedicos() {
  const tbody = document.getElementById('tabla-medicos');
  tbody.innerHTML = `<tr><td colspan="6"><div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div></td></tr>`;
  try {
    const incluirInactivos = document.getElementById('filtro-medicos-inactivos')?.checked || false;
    medicosData = await Api.getMedicos({ incluirInactivos });
    renderMedicos(medicosData);
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="6"><div class="alert alert-error">${e.message}</div></td></tr>`;
  }
}

document.getElementById('filtro-medicos-inactivos')?.addEventListener('change', loadMedicos);

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
      <td><span class="badge ${m.visible ? 'badge-activo' : 'badge-inactivo'}">${m.visible ? 'Activo' : 'Inactivo'}</span></td>
      <td>
        <div class="table-actions">
          <button class="btn-icon" title="Editar" onclick="editarMedico(${m.idMedico})">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2"/></svg>
          </button>
          ${m.visible
            ? `<button class="btn-icon" title="Dar de baja" onclick="bajaMedico(${m.idMedico})" style="color:var(--rojo)">
                 <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><polyline points="3 6 5 6 21 6" stroke="currentColor" stroke-width="2"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" stroke="currentColor" stroke-width="2"/></svg>
               </button>`
            : `<button class="btn-icon" title="Reactivar" onclick="reactivarMedicoAccion(${m.idMedico})" style="color:var(--verde)">
                 <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><polyline points="23 4 23 10 17 10" stroke="currentColor" stroke-width="2"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" stroke="currentColor" stroke-width="2"/></svg>
               </button>`}
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
let medicoObrasSocialesRows = [];
let medicoObrasSocialesEliminadas = [];
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

async function ensureObrasSocialesCache() {
  if (!medicoObrasSocialesCache.length) {
    medicoObrasSocialesCache = await Api.getObrasSociales().catch(() => []);
  }
  return medicoObrasSocialesCache;
}

function renderObrasSocialesTable() {
  const tbody = document.getElementById('medico-obrassociales-tbody');
  tbody.innerHTML = medicoObrasSocialesRows.map((row, idx) => `
    <tr>
      <td>
        <select onchange="medicoObrasSocialesRows[${idx}].idObraSocial = parseInt(this.value) || null">
          <option value="">Seleccioná…</option>
          ${medicoObrasSocialesCache.map(os => `<option value="${os.idObraSocial}" ${row.idObraSocial === os.idObraSocial ? 'selected' : ''}>${os.nombre}</option>`).join('')}
        </select>
      </td>
      <td><input type="number" min="0" step="0.01" value="${row.importeCoseguro ?? ''}" placeholder="Coseguro" onchange="medicoObrasSocialesRows[${idx}].importeCoseguro = parseFloat(this.value) || null" /></td>
      <td><button type="button" class="btn-icon" title="Quitar" onclick="quitarFilaObraSocial(${idx})">✕</button></td>
    </tr>`).join('') || '<tr><td colspan="3" style="color:var(--gris-medio)">Sin obras sociales agregadas.</td></tr>';
}

function agregarFilaObraSocial(idObraSocial) {
  medicoObrasSocialesRows.push({ idMedicoObraSocial: null, idObraSocial: idObraSocial || null, importeCoseguro: null });
  renderObrasSocialesTable();
}

function quitarFilaObraSocial(idx) {
  const row = medicoObrasSocialesRows[idx];
  if (row.idMedicoObraSocial) {
    medicoObrasSocialesEliminadas.push(row.idMedicoObraSocial);
  }
  medicoObrasSocialesRows.splice(idx, 1);
  renderObrasSocialesTable();
}

async function confirmarNuevaObraSocialMedico() {
  const nombre = document.getElementById('medico-obra-social-nueva-nombre').value.trim();
  if (!nombre) {
    Toast.error('Ingresá un nombre para la nueva obra social.');
    return;
  }
  try {
    const nueva = await Api.createObraSocial({ nombre });
    medicoObrasSocialesCache.push(nueva);
    document.getElementById('medico-obra-social-nueva-nombre').value = '';
    document.getElementById('medico-obra-social-nueva-inline').classList.add('hidden');
    agregarFilaObraSocial(nueva.idObraSocial);
    Toast.success('Obra social agregada al catálogo.');
  } catch (e) {
    Toast.error(e.message);
  }
}

function renderPrestacionesTable() {
  const tbody = document.getElementById('medico-prestaciones-tbody');
  tbody.innerHTML = medicoPrestacionesRows.map((row, idx) => `
    <tr>
      <td><input type="text" value="${row.nombrePrestacion || ''}" onchange="medicoPrestacionesRows[${idx}].nombrePrestacion = this.value" placeholder="Nombre de la prestación" /></td>
      <td><input type="number" min="0" value="${row.duracionEstimadaMin || ''}" onchange="medicoPrestacionesRows[${idx}].duracionEstimadaMin = parseInt(this.value) || null" /></td>
      <td><input type="number" min="0" step="0.01" value="${row.importeParticular || ''}" onchange="medicoPrestacionesRows[${idx}].importeParticular = parseFloat(this.value) || null" /></td>
      <td><button type="button" class="btn-icon" title="Quitar" onclick="quitarFilaPrestacion(${idx})">✕</button></td>
    </tr>`).join('') || '<tr><td colspan="4" style="color:var(--gris-medio)">Sin prestaciones agregadas.</td></tr>';
}

function agregarFilaPrestacion() {
  medicoPrestacionesRows.push({ idMedicoPrestacion: null, idPrestacion: null, nombrePrestacion: '', duracionEstimadaMin: null, importeParticular: null });
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
  medicoObrasSocialesRows = [];
  medicoObrasSocialesEliminadas = [];
  await Promise.all([populateEspecialidadSelect(), ensureObrasSocialesCache()]);
  renderObrasSocialesTable();
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
  medicoObrasSocialesRows = (m.obrasSociales || []).map(os => ({ ...os }));
  medicoObrasSocialesEliminadas = [];
  await Promise.all([
    populateEspecialidadSelect(m.idEspecialidad),
    ensureObrasSocialesCache()
  ]);
  renderObrasSocialesTable();
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
  };
  if (!payload.nombre || !payload.apellido || !payload.matricula || !payload.email || !payload.idEspecialidad || !payload.fechaInicioActividad) {
    Toast.error('Completá los campos obligatorios (nombre, apellido, matrícula, especialidad, inicio de actividad y email).');
    return;
  }
  if (medicoObrasSocialesRows.some(row => !row.idObraSocial)) {
    Toast.error('Seleccioná una obra social para cada fila agregada (o quitá la fila vacía).');
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
      // Obras sociales: alta/edición (coseguro)/baja lógica vía endpoints dedicados
      for (const idEliminado of medicoObrasSocialesEliminadas) {
        await Api.deleteMedicoObraSocial(idEliminado);
      }
      for (const row of medicoObrasSocialesRows) {
        if (row.idMedicoObraSocial) {
          await Api.updateMedicoObraSocial(row.idMedicoObraSocial, row);
        } else {
          await Api.addMedicoObraSocial(editingMedicoId, row);
        }
      }
      Toast.success('Médico actualizado.');
    } else {
      payload.prestaciones = medicoPrestacionesRows;
      payload.obrasSociales = medicoObrasSocialesRows;
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

async function reactivarMedicoAccion(id) {
  try {
    await Api.reactivarMedico(id);
    Toast.success('Médico reactivado.');
    loadMedicos();
  } catch (e) {
    Toast.error(e.message);
  }
}

// ---- Mis Prestaciones (Médico — gestión propia de la cartilla) ----
// El médico da de alta/edita/quita sus propias prestaciones (RF-M4 / relevamiento: la hoja del
// médico "es actualizable cada vez que el médico lo desee"). Tabla de lectura + alta/edición por
// modal (mismo patrón que el resto de los módulos). Gerente/Administrativo lo hacen desde Médicos.
let misPrestacionesData = [];
let editandoMiPrestacionId = null;

const fmtMoneda = (v) => v == null ? null : '$' + Number(v).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

async function loadMisPrestaciones() {
  const container = document.getElementById('mis-prestaciones-container');
  container.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div>`;
  try {
    misPrestacionesData = await Api.getMisPrestaciones() || [];
    container.innerHTML = `
      <div class="card cartilla-card">
        <div class="card-header">
          <div>
            <span class="card-title">Prestaciones que ofrecés</span>
            <p class="card-subtitle">Consultas y estudios que realizás, con su duración e importe particular.</p>
          </div>
          <button class="btn btn-primary btn-sm" onclick="abrirModalMiPrestacion()">+ Agregar prestación</button>
        </div>
        <div class="table-wrapper">
          <table>
            <thead><tr>
              <th>Prestación</th>
              <th style="width:130px;">Duración</th>
              <th style="width:170px;">Importe particular</th>
              <th style="width:96px;"></th>
            </tr></thead>
            <tbody id="mis-prestaciones-tbody"></tbody>
          </table>
        </div>
      </div>`;
    renderMisPrestacionesTable();
  } catch (e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

function renderMisPrestacionesTable() {
  const tbody = document.getElementById('mis-prestaciones-tbody');
  if (!tbody) return;
  if (misPrestacionesData.length === 0) {
    tbody.innerHTML = `<tr><td colspan="4"><div class="empty-state" style="padding:var(--sp-8) var(--sp-4);"><h3>Todavía no cargaste prestaciones</h3><p>Agregá la primera con el botón “+ Agregar prestación”.</p></div></td></tr>`;
    return;
  }
  tbody.innerHTML = misPrestacionesData.map(p => `
    <tr>
      <td><strong>${p.nombrePrestacion || '—'}</strong></td>
      <td class="num-cell">${p.duracionEstimadaMin != null ? p.duracionEstimadaMin + ' min' : '<span class="muted">—</span>'}</td>
      <td class="num-cell">${fmtMoneda(p.importeParticular) ?? '<span class="muted">—</span>'}</td>
      <td class="cartilla-acciones">
        <button type="button" class="btn-icon" title="Editar" onclick="abrirModalMiPrestacion(${p.idMedicoPrestacion})">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2"/></svg>
        </button>
        <button type="button" class="btn-icon" title="Quitar" style="color:var(--rojo)" onclick="quitarMiPrestacion(${p.idMedicoPrestacion})">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><polyline points="3 6 5 6 21 6" stroke="currentColor" stroke-width="2"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" stroke="currentColor" stroke-width="2"/></svg>
        </button>
      </td>
    </tr>`).join('');
}

function abrirModalMiPrestacion(idMedicoPrestacion) {
  const p = idMedicoPrestacion ? misPrestacionesData.find(x => x.idMedicoPrestacion === idMedicoPrestacion) : null;
  editandoMiPrestacionId = p ? p.idMedicoPrestacion : null;
  document.getElementById('modal-mi-prestacion-title').textContent = p ? 'Editar prestación' : 'Agregar prestación';
  document.getElementById('mi-prestacion-nombre-group').classList.toggle('hidden', !!p);
  document.getElementById('mi-prestacion-nombre-fijo-group').classList.toggle('hidden', !p);
  document.getElementById('mi-prestacion-nombre').value = '';
  document.getElementById('mi-prestacion-nombre-fijo').textContent = p ? p.nombrePrestacion : '';
  document.getElementById('mi-prestacion-duracion').value = p?.duracionEstimadaMin ?? '';
  document.getElementById('mi-prestacion-importe').value = p?.importeParticular ?? '';
  openModal('modal-mi-prestacion');
}

async function guardarMiPrestacion() {
  const btn = document.getElementById('btn-guardar-mi-prestacion');
  const payload = {
    duracionEstimadaMin: parseInt(document.getElementById('mi-prestacion-duracion').value) || null,
    importeParticular: parseFloat(document.getElementById('mi-prestacion-importe').value) || null,
  };
  if (!editandoMiPrestacionId) {
    const nombre = document.getElementById('mi-prestacion-nombre').value.trim();
    if (!nombre) { Toast.error('Indicá el nombre de la prestación.'); return; }
    payload.nombrePrestacion = nombre;
  }
  btn.disabled = true;
  try {
    if (editandoMiPrestacionId) {
      await Api.updateMiPrestacion(editandoMiPrestacionId, payload);
      Toast.success('Prestación actualizada.');
    } else {
      await Api.addMiPrestacion(payload);
      Toast.success('Prestación agregada.');
    }
    closeModal('modal-mi-prestacion');
    loadMisPrestaciones();
  } catch (e) {
    Toast.error(e.message);
  } finally {
    btn.disabled = false;
  }
}

async function quitarMiPrestacion(idMedicoPrestacion) {
  const p = misPrestacionesData.find(x => x.idMedicoPrestacion === idMedicoPrestacion);
  if (!confirm(`¿Quitar la prestación "${p ? p.nombrePrestacion : ''}" de tu cartilla?`)) return;
  try {
    await Api.deleteMiPrestacion(idMedicoPrestacion);
    Toast.success('Prestación quitada.');
    loadMisPrestaciones();
  } catch (e) {
    Toast.error(e.message);
  }
}

// ---- Mis Datos (Médico) ----
// Solo Nombre/Apellido de los datos personales son editables por el propio médico: Especialidad
// e Importe de Consulta afectan RN-006 y la habilitación por especialidad, así que quedan de
// solo lectura acá (se editan desde el módulo Médicos, Gerente/Administrativo). La cartilla de
// obras sociales (agregar/quitar + coseguro de cada una) sí la gestiona el propio médico
// (RN-017), vía /api/medicos/me/obras-sociales.

async function loadMisDatos() {
  const container = document.getElementById('mis-datos-container');
  container.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div>`;
  const rol = getRol();
  try {
    if (rol === 'MEDICO') {
      const m = await Api.getMisDatosMedico();
      const esc = (s) => String(s ?? '').replace(/"/g, '&quot;');
      container.innerHTML = `
        <div class="card cartilla-card">
          <div class="card-header">
            <div>
              <span class="card-title">Datos personales</span>
              <p class="card-subtitle">Matrícula, especialidad e importe de consulta los gestiona Administración.</p>
            </div>
          </div>
          <dl class="datos-grid">
            <div><dt>Matrícula</dt><dd class="mono">${m.matricula || '—'}</dd></div>
            <div><dt>Especialidad</dt><dd>${m.nombreEspecialidad || '—'}</dd></div>
            <div><dt>Email</dt><dd>${m.email || '—'}</dd></div>
            <div><dt>Importe de consulta</dt><dd class="mono">$${Number(m.importeConsulta || 0).toLocaleString('es-AR', {minimumFractionDigits:2})}</dd></div>
            <div><dt>Inicio de actividad</dt><dd class="mono">${m.fechaInicioActividad || '—'}</dd></div>
            <div><dt>Estado</dt><dd>${m.estado || '—'}</dd></div>
          </dl>
          <div class="form-row">
            <div class="form-group"><label>Nombre</label><input type="text" id="misdatos-nombre" value="${esc(m.nombre)}" /></div>
            <div class="form-group"><label>Apellido</label><input type="text" id="misdatos-apellido" value="${esc(m.apellido)}" /></div>
          </div>
          <div style="margin-top:var(--sp-4);">
            <button class="btn btn-primary" id="btn-guardar-misdatos" onclick="guardarMisDatos()">Guardar nombre y apellido</button>
          </div>
        </div>

        <div class="card cartilla-card">
          <div class="card-header">
            <div>
              <span class="card-title">Obras sociales con las que trabajás</span>
              <p class="card-subtitle">El coseguro que cobrás en cada una lo decidís vos.</p>
            </div>
          </div>
          <div class="table-wrapper">
            <table id="misdatos-obrassociales-table">
              <thead><tr><th>Obra social</th><th style="width:180px;">Coseguro</th><th style="width:130px;"></th></tr></thead>
              <tbody id="misdatos-obrassociales-tbody"></tbody>
            </table>
          </div>
          <div class="cartilla-addbar">
            <div class="form-group os-field">
              <label>Agregar obra social</label>
              <select id="misdatos-os-nueva-select"><option value="">Seleccioná…</option></select>
            </div>
            <div class="form-group cos-field">
              <label>Coseguro</label>
              <input type="number" min="0" step="0.01" id="misdatos-os-nueva-coseguro" placeholder="Ej: 1500" />
            </div>
            <button type="button" class="btn btn-primary" onclick="agregarMiObraSocial()">Agregar</button>
          </div>
        </div>`;
      await renderMisObrasSociales();
    } else {
      container.innerHTML = `<div class="empty-state"><h3>No disponible</h3><p>No hay datos personales para este rol.</p></div>`;
    }
  } catch (e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

let misObrasSocialesData = [];

async function renderMisObrasSociales() {
  const tbody = document.getElementById('misdatos-obrassociales-tbody');
  tbody.innerHTML = `<tr><td colspan="3"><div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div></td></tr>`;
  try {
    const [obrasSociales] = await Promise.all([Api.getMisObrasSociales(), ensureObrasSocialesCache()]);
    misObrasSocialesData = obrasSociales || [];
    if (misObrasSocialesData.length === 0) {
      tbody.innerHTML = `<tr><td colspan="3"><div class="empty-state" style="padding:var(--sp-8) var(--sp-4);"><h3>Sin obras sociales en tu cartilla</h3><p>Agregá una desde el selector de abajo.</p></div></td></tr>`;
    } else {
      tbody.innerHTML = misObrasSocialesData.map(os => `
        <tr>
          <td><strong>${os.nombreObraSocial}</strong></td>
          <td><input type="number" min="0" step="0.01" class="cell-input" id="mis-coseguro-${os.idMedicoObraSocial}" value="${os.importeCoseguro ?? ''}" placeholder="—" onkeydown="if(event.key==='Enter')guardarMiCoseguro(${os.idMedicoObraSocial})" /></td>
          <td class="cartilla-acciones">
            <button type="button" class="btn btn-secondary btn-sm" onclick="guardarMiCoseguro(${os.idMedicoObraSocial})">Guardar</button>
            <button type="button" class="btn-icon" title="Quitar de mi cartilla" style="color:var(--rojo)" onclick="quitarMiObraSocial(${os.idMedicoObraSocial})">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><polyline points="3 6 5 6 21 6" stroke="currentColor" stroke-width="2"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" stroke="currentColor" stroke-width="2"/></svg>
            </button>
          </td>
        </tr>`).join('');
    }
    // Poblar el select de "agregar" con las obras sociales del catálogo que todavía no tengo.
    const sel = document.getElementById('misdatos-os-nueva-select');
    if (sel) {
      const yaTengo = new Set(misObrasSocialesData.map(os => os.idObraSocial));
      const disponibles = medicoObrasSocialesCache.filter(os => !yaTengo.has(os.idObraSocial));
      sel.innerHTML = '<option value="">Seleccioná…</option>' +
        disponibles.map(os => `<option value="${os.idObraSocial}">${os.nombre}</option>`).join('');
    }
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="3"><div class="alert alert-error">${e.message}</div></td></tr>`;
  }
}

async function guardarMiCoseguro(idMedicoObraSocial) {
  const input = document.getElementById(`mis-coseguro-${idMedicoObraSocial}`);
  const importeCoseguro = parseFloat(input.value) || null;
  try {
    await Api.updateMiCoseguro(idMedicoObraSocial, { importeCoseguro });
    Toast.success('Coseguro actualizado.');
  } catch (e) {
    Toast.error(e.message);
  }
}

async function agregarMiObraSocial() {
  const idObraSocial = parseInt(document.getElementById('misdatos-os-nueva-select').value) || null;
  const importeCoseguro = parseFloat(document.getElementById('misdatos-os-nueva-coseguro').value) || null;
  if (!idObraSocial) {
    Toast.error('Elegí una obra social del listado.');
    return;
  }
  try {
    await Api.addMiObraSocial({ idObraSocial, importeCoseguro });
    Toast.success('Obra social agregada a tu cartilla.');
    document.getElementById('misdatos-os-nueva-coseguro').value = '';
    document.getElementById('misdatos-os-nueva-select').value = '';
    await renderMisObrasSociales();
  } catch (e) {
    Toast.error(e.message);
  }
}

async function quitarMiObraSocial(idMedicoObraSocial) {
  const os = misObrasSocialesData.find(x => x.idMedicoObraSocial === idMedicoObraSocial);
  if (!confirm(`¿Quitar "${os ? os.nombreObraSocial : 'esta obra social'}" de tu cartilla?`)) return;
  try {
    await Api.deleteMiObraSocial(idMedicoObraSocial);
    Toast.success('Obra social quitada de tu cartilla.');
    await renderMisObrasSociales();
  } catch (e) {
    Toast.error(e.message);
  }
}

async function guardarMisDatos() {
  const btn = document.getElementById('btn-guardar-misdatos');
  const payload = {
    nombre: document.getElementById('misdatos-nombre').value.trim(),
    apellido: document.getElementById('misdatos-apellido').value.trim(),
  };
  if (!payload.nombre || !payload.apellido) {
    Toast.error('Nombre y apellido son obligatorios.');
    return;
  }
  btn.disabled = true;
  try {
    await Api.updateMisDatos(payload);
    Toast.success('Datos actualizados.');
    loadMisDatos();
  } catch (e) {
    Toast.error(e.message);
  } finally {
    btn.disabled = false;
  }
}
