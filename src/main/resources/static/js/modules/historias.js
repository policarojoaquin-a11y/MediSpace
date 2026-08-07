// ==================== HISTORIAS CLÍNICAS MODULE ====================
let currentHC = null;
let currentEvolucionId = null;

async function buscarHistoriaClinica() {
  const q = document.getElementById('search-hc-paciente')?.value?.trim();
  if (!q) { Toast.warning('Ingresá un nombre o DNI para buscar.'); return; }

  // Search in pacientes
  const container = document.getElementById('hc-container');
  container.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Buscando…</span></div>`;
  try {
    const pacientes = await Api.getPacientes();
    const match = pacientes.find(p =>
      `${p.nombre} ${p.apellido}`.toLowerCase().includes(q.toLowerCase()) ||
      (p.dni && p.dni.includes(q))
    );
    if (!match) {
      container.innerHTML = `<div class="empty-state"><svg width="40" height="40" viewBox="0 0 24 24" fill="none"><circle cx="11" cy="11" r="8" stroke="currentColor" stroke-width="2"/><line x1="21" y1="21" x2="16.65" y2="16.65" stroke="currentColor" stroke-width="2"/></svg><h3>Paciente no encontrado</h3><p>Verificá el nombre o DNI ingresado.</p></div>`;
      return;
    }
    verHistoriaClinica(match.idPaciente, match);
  } catch (e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

async function verHistoriaClinica(idPaciente, paciente = null) {
  const container = document.getElementById('hc-container');
  container.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Cargando historia clínica…</span></div>`;
  try {
    const hc = await Api.getHistoriaClinica(idPaciente);
    currentHC = hc;
    renderHistoriaClinica(hc, paciente);
  } catch (e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

function renderHistoriaClinica(hc, paciente) {
  const rol = getRol();
  const canEdit = rol === 'MEDICO' || rol === 'GERENTE';
  const container = document.getElementById('hc-container');

  const pacNombre = paciente
    ? `${paciente.apellido}, ${paciente.nombre}`
    : `Paciente #${hc.idPaciente}`;

  const evolHtml = (hc.evoluciones || []).length === 0
    ? `<div class="empty-state" style="padding:32px"><svg width="36" height="36" viewBox="0 0 24 24" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" stroke-width="2"/></svg><h3>Sin evoluciones</h3><p>Registrá la primera evolución.</p></div>`
    : (hc.evoluciones || []).map(ev => renderEvolucion(ev, canEdit)).join('');

  container.innerHTML = `
    <div class="card" style="margin-bottom:16px;">
      <div class="card-header">
        <div>
          <div class="card-title">HC — ${pacNombre}</div>
          <div class="card-subtitle">Nro. Historia: ${hc.numeroHistoria || hc.idHistoriaClinica} | DNI: ${paciente?.dni || '—'}</div>
        </div>
        ${canEdit ? `<button class="btn btn-primary btn-sm" onclick="abrirNuevaEvolucion(${hc.idHistoriaClinica})">+ Evolución</button>` : ''}
      </div>
    </div>
    <div id="evoluciones-list">${evolHtml}</div>
  `;
}

function renderEvolucion(ev, canEdit) {
  const fecha = ev.fechaEvolucion
    ? new Date(ev.fechaEvolucion).toLocaleString('es-AR', { dateStyle:'medium', timeStyle:'short' })
    : '—';
  const anulada = ev.estado === 'ANULADA';
  return `
    <div class="card" style="margin-bottom:12px;${anulada ? 'opacity:.6;border-left:3px solid var(--rojo);' : ''}">
      <div style="display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:12px;">
        <div>
          <strong style="color:var(--azul)">${ev.motivoConsulta || 'Consulta'}</strong>
          <span class="text-muted text-sm" style="margin-left:10px;">${fecha}</span>
          ${anulada ? '<span class="badge badge-anulada" style="margin-left:8px;">ANULADA</span>' : ''}
        </div>
        ${canEdit && !anulada ? `
          <div class="table-actions">
            <button class="btn-icon" title="Editar" onclick="editarEvolucion(${JSON.stringify(ev).replace(/"/g,'&quot;')})">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2"/></svg>
            </button>
            <button class="btn-icon" title="Anular" onclick="anularEvolucion(${ev.idEvolucion})" style="color:var(--rojo)">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><polyline points="3 6 5 6 21 6" stroke="currentColor" stroke-width="2"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" stroke="currentColor" stroke-width="2"/></svg>
            </button>
          </div>` : ''}
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">
        ${ev.diagnostico ? `<div><span class="text-muted text-sm">Diagnóstico</span><p style="margin-top:2px">${ev.diagnostico}</p></div>` : ''}
        ${ev.tratamiento ? `<div><span class="text-muted text-sm">Tratamiento</span><p style="margin-top:2px">${ev.tratamiento}</p></div>` : ''}
        ${ev.indicaciones ? `<div><span class="text-muted text-sm">Indicaciones</span><p style="margin-top:2px">${ev.indicaciones}</p></div>` : ''}
        ${ev.estudiosSolicitados ? `<div><span class="text-muted text-sm">Estudios</span><p style="margin-top:2px">${ev.estudiosSolicitados}</p></div>` : ''}
      </div>
      ${ev.observaciones ? `<p class="text-muted text-sm" style="margin-top:8px;padding-top:8px;border-top:1px solid var(--gris-borde)">${ev.observaciones}</p>` : ''}
    </div>`;
}

function abrirNuevaEvolucion(idHC) {
  currentEvolucionId = null;
  document.getElementById('modal-evolucion-title').textContent = 'Nueva Evolución';
  document.getElementById('evol-id-hc').value = idHC;
  document.getElementById('evol-id').value = '';
  ['evol-motivo','evol-diagnostico','evol-tratamiento','evol-indicaciones','evol-estudios','evol-observaciones'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.value = '';
  });
  openModal('modal-evolucion');
}

function editarEvolucion(ev) {
  currentEvolucionId = ev.idEvolucion;
  document.getElementById('modal-evolucion-title').textContent = 'Editar Evolución';
  document.getElementById('evol-id-hc').value     = currentHC?.idHistoriaClinica || '';
  document.getElementById('evol-id').value        = ev.idEvolucion;
  document.getElementById('evol-motivo').value    = ev.motivoConsulta || '';
  document.getElementById('evol-diagnostico').value = ev.diagnostico || '';
  document.getElementById('evol-tratamiento').value = ev.tratamiento || '';
  document.getElementById('evol-indicaciones').value = ev.indicaciones || '';
  document.getElementById('evol-estudios').value   = ev.estudiosSolicitados || '';
  document.getElementById('evol-observaciones').value = ev.observaciones || '';
  openModal('modal-evolucion');
}

async function guardarEvolucion() {
  const idHC = document.getElementById('evol-id-hc').value;
  const idEvol = document.getElementById('evol-id').value;
  const payload = {
    motivoConsulta:     document.getElementById('evol-motivo').value.trim(),
    diagnostico:        document.getElementById('evol-diagnostico').value.trim(),
    tratamiento:        document.getElementById('evol-tratamiento').value.trim(),
    indicaciones:       document.getElementById('evol-indicaciones').value.trim(),
    estudiosSolicitados:document.getElementById('evol-estudios').value.trim(),
    observaciones:      document.getElementById('evol-observaciones').value.trim(),
  };
  if (!payload.motivoConsulta || !payload.diagnostico) {
    Toast.error('Motivo de consulta y diagnóstico son obligatorios.'); return;
  }
  try {
    if (idEvol) {
      await Api.editEvolucion(idEvol, payload);
      Toast.success('Evolución actualizada.');
    } else {
      await Api.addEvolucion(idHC, payload);
      Toast.success('Evolución registrada.');
    }
    closeModal('modal-evolucion');
    verHistoriaClinica(currentHC.idPaciente);
  } catch (e) {
    Toast.error(e.message);
  }
}

async function anularEvolucion(idEvol) {
  const motivo = prompt('Ingresá el motivo de la anulación:');
  if (!motivo?.trim()) return;
  try {
    await Api.anularEvolucion(idEvol, motivo);
    Toast.success('Evolución anulada.');
    verHistoriaClinica(currentHC.idPaciente);
  } catch (e) {
    Toast.error(e.message);
  }
}
