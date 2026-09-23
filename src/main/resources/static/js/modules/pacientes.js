// ==================== PACIENTES MODULE ====================
let pacientesData = [];
let editingPacienteId = null;
let obrasSocialesCache = [];

async function populateObraSocialSelect(selectedId) {
  const sel = document.getElementById('pac-obra-social');
  if (!obrasSocialesCache.length) {
    obrasSocialesCache = await Api.getObrasSociales().catch(() => []);
  }
  sel.innerHTML = '<option value="">Particular</option>';
  obrasSocialesCache.forEach(os => {
    const opt = document.createElement('option');
    opt.value = os.idObraSocial;
    opt.textContent = os.nombre;
    sel.appendChild(opt);
  });
  const optNueva = document.createElement('option');
  optNueva.value = '__new__';
  optNueva.textContent = '+ Nueva obra social…';
  sel.appendChild(optNueva);
  sel.value = selectedId || '';
  document.getElementById('pac-obra-social-nueva-inline')?.classList.add('hidden');
}

document.getElementById('pac-obra-social')?.addEventListener('change', (e) => {
  const inline = document.getElementById('pac-obra-social-nueva-inline');
  if (e.target.value === '__new__') {
    inline.classList.remove('hidden');
    document.getElementById('pac-obra-social-nueva-nombre').value = '';
    document.getElementById('pac-obra-social-nueva-nombre').focus();
  } else {
    inline.classList.add('hidden');
  }
});

async function confirmarNuevaObraSocialPaciente() {
  const nombre = document.getElementById('pac-obra-social-nueva-nombre').value.trim();
  if (!nombre) {
    Toast.error('Ingresá un nombre para la nueva obra social.');
    return;
  }
  try {
    const nueva = await Api.createObraSocial({ nombre });
    obrasSocialesCache.push(nueva);
    await populateObraSocialSelect(nueva.idObraSocial);
    Toast.success('Obra social agregada.');
  } catch (e) {
    Toast.error(e.message);
  }
}

function badgeEstadoPaciente(estado) {
  const cls = estado === 'ACTIVO' ? 'badge-activo' : 'badge-inactivo';
  return `<span class="badge ${cls}">${estado}</span>`;
}

async function loadPacientes() {
  // M1 (spec.md §2.1): GERENTE quedó solo lectura en Pacientes — el backend ya devuelve 403
  // para alta/edición/baja, se oculta el botón como defensa en profundidad.
  const btnNuevo = document.getElementById('btn-nuevo-paciente');
  if (btnNuevo) btnNuevo.classList.toggle('hidden', getRol() === 'GERENTE');

  const tbody = document.getElementById('tabla-pacientes');
  tbody.innerHTML = `<tr><td colspan="6"><div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div></td></tr>`;
  try {
    pacientesData = await Api.getPacientes();
    renderPacientes(pacientesData);
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="6"><div class="alert alert-error">${e.message}</div></td></tr>`;
  }
}

function renderPacientes(data) {
  const tbody = document.getElementById('tabla-pacientes');
  if (!data || data.length === 0) {
    tbody.innerHTML = `<tr><td colspan="6"><div class="empty-state"><svg width="40" height="40" viewBox="0 0 24 24" fill="none"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" stroke-width="2"/><circle cx="9" cy="7" r="4" stroke="currentColor" stroke-width="2"/></svg><h3>Sin pacientes</h3><p>Registrá el primer paciente.</p></div></td></tr>`;
    return;
  }
  // Sección 4.5: GERENTE no tiene acceso a Historias Clínicas ("No" en la tabla de permisos).
  // El backend ya devuelve 403 para este rol — se oculta el botón como defensa en profundidad,
  // la validación real sigue siendo la del backend.
  const puedeVerHC = getRol() !== 'GERENTE';
  // M1 (spec.md §2.1): GERENTE quedó solo lectura en Pacientes — mismo criterio que arriba.
  const puedeEditar = getRol() !== 'GERENTE';

  tbody.innerHTML = data.map(p => `
    <tr>
      <td><strong>${p.apellido}, ${p.nombre}</strong></td>
      <td>${p.dni || '—'}</td>
      <td>${p.telefono || '—'}</td>
      <td>${p.nombreObraSocial || 'Particular'}</td>
      <td>${badgeEstadoPaciente(p.estado)}</td>
      <td>
        <div class="table-actions">
          ${puedeEditar ? `
          <button class="btn-icon" title="Editar" onclick="editarPaciente(${p.idPaciente})">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2"/></svg>
          </button>` : ''}
          ${puedeVerHC ? `
          <button class="btn-icon" title="Historia Clínica" onclick="abrirHCPaciente(${p.idPaciente})" style="color:var(--azul)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" stroke-width="2"/><polyline points="14 2 14 8 20 8" stroke="currentColor" stroke-width="2"/></svg>
          </button>` : ''}
          ${puedeEditar ? `
          <button class="btn-icon" title="Dar de baja" onclick="bajaPaciente(${p.idPaciente})" style="color:var(--rojo)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><polyline points="3 6 5 6 21 6" stroke="currentColor" stroke-width="2"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" stroke="currentColor" stroke-width="2"/></svg>
          </button>` : ''}
        </div>
      </td>
    </tr>`).join('');
}

// Search
document.getElementById('search-pacientes')?.addEventListener('input', (e) => {
  const q = e.target.value.toLowerCase();
  const filtered = pacientesData.filter(p =>
    `${p.nombre} ${p.apellido} ${p.dni}`.toLowerCase().includes(q)
  );
  renderPacientes(filtered);
});

// Open modal new
document.getElementById('btn-nuevo-paciente')?.addEventListener('click', async () => {
  editingPacienteId = null;
  document.getElementById('modal-paciente-title').textContent = 'Nuevo Paciente';
  ['pac-nombre','pac-apellido','pac-dni','pac-telefono','pac-credencial','pac-plan','pac-direccion'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.value = '';
  });
  // Alta: el documento (DNI / Pasaporte) sí se puede escribir. En edición se bloquea (RN-008).
  const dniEl = document.getElementById('pac-dni');
  dniEl.readOnly = false;
  dniEl.removeAttribute('title');
  dniEl.classList.remove('input-readonly');
  document.getElementById('pac-fnac').value = '';
  document.getElementById('pac-estado').value = 'ACTIVO';
  await populateObraSocialSelect();
  openModal('modal-paciente');
});

async function editarPaciente(id) {
  const p = pacientesData.find(x => x.idPaciente === id);
  if (!p) return;
  editingPacienteId = id;
  document.getElementById('modal-paciente-title').textContent = 'Editar Paciente';
  document.getElementById('pac-nombre').value      = p.nombre || '';
  document.getElementById('pac-apellido').value    = p.apellido || '';
  // RN-008: el documento (DNI / Pasaporte) es inmutable una vez creado — no editable directamente.
  const dniEl = document.getElementById('pac-dni');
  dniEl.value = p.dni || '';
  dniEl.readOnly = true;
  dniEl.title = 'El documento (DNI / Pasaporte) no puede modificarse una vez creado el paciente (RN-008).';
  dniEl.classList.add('input-readonly');
  document.getElementById('pac-fnac').value        = p.fechaNacimiento || '';
  document.getElementById('pac-telefono').value    = p.telefono || '';
  document.getElementById('pac-credencial').value  = p.numeroCredencial || '';
  document.getElementById('pac-plan').value        = p.planOs || '';
  document.getElementById('pac-direccion').value   = p.direccion || '';
  document.getElementById('pac-estado').value      = p.estado || 'ACTIVO';
  await populateObraSocialSelect(p.idObraSocial);
  openModal('modal-paciente');
}

async function guardarPaciente() {
  const btn = document.getElementById('btn-guardar-paciente');
  const payload = {
    nombre:          document.getElementById('pac-nombre').value.trim(),
    apellido:        document.getElementById('pac-apellido').value.trim(),
    dni:             document.getElementById('pac-dni').value.trim(),
    fechaNacimiento: document.getElementById('pac-fnac').value,
    telefono:        document.getElementById('pac-telefono').value.trim(),
    idObraSocial:    parseInt(document.getElementById('pac-obra-social').value) || null,
    numeroCredencial:document.getElementById('pac-credencial').value.trim(),
    planOs:          document.getElementById('pac-plan').value.trim(),
    direccion:       document.getElementById('pac-direccion').value.trim(),
    estado:          document.getElementById('pac-estado').value,
  };
  if (!payload.nombre || !payload.apellido || !payload.dni || !payload.fechaNacimiento) {
    Toast.error('Completá los campos obligatorios (nombre, apellido, DNI / Pasaporte y fecha de nacimiento).');
    return;
  }
  btn.disabled = true;
  try {
    if (editingPacienteId) {
      await Api.updatePaciente(editingPacienteId, payload);
      Toast.success('Paciente actualizado correctamente.');
    } else {
      await Api.createPaciente(payload);
      Toast.success('Paciente registrado. Se creó la Historia Clínica automáticamente.');
    }
    closeModal('modal-paciente');
    loadPacientes();
  } catch (e) {
    Toast.error(e.message);
  } finally {
    btn.disabled = false;
  }
}

async function bajaPaciente(id) {
  if (!confirm('¿Dar de baja al paciente? Esta acción es reversible.')) return;
  try {
    await Api.deletePaciente(id);
    Toast.success('Paciente dado de baja.');
    loadPacientes();
  } catch (e) {
    Toast.error(e.message);
  }
}

function abrirHCPaciente(idPaciente) {
  navigateTo('historias', 'Historias Clínicas');
  setTimeout(() => verHistoriaClinica(idPaciente), 200);
}
