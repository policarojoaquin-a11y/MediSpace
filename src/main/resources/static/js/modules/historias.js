// ==================== HISTORIAS CLÍNICAS MODULE ====================
let currentHC = null;
let currentEvolucionId = null;
let evolucionAdjuntosActuales = [];

async function buscarHistoriaClinica() {
  const q = document.getElementById('search-hc-paciente')?.value?.trim();
  if (!q) { Toast.warning('Ingresá un nombre o documento para buscar.'); return; }

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
      container.innerHTML = `<div class="empty-state"><svg width="40" height="40" viewBox="0 0 24 24" fill="none"><circle cx="11" cy="11" r="8" stroke="currentColor" stroke-width="2"/><line x1="21" y1="21" x2="16.65" y2="16.65" stroke="currentColor" stroke-width="2"/></svg><h3>Paciente no encontrado</h3><p>Verificá el nombre o documento ingresado.</p></div>`;
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
  // Solo el MEDICO responsable escribe evoluciones (RF-H2). ADMINISTRATIVO ve el contenido
  // completo pero no edita; GERENTE no llega acá (backend devuelve 403).
  const canEdit = rol === 'MEDICO';
  const container = document.getElementById('hc-container');

  // El DTO de la HC ya trae nombre y documento del paciente — usarlos como fuente primaria; el
  // objeto `paciente` (opcional) es solo un fallback cuando se entra desde el listado.
  const pacNombre = hc.nombrePaciente
    || (paciente ? `${paciente.apellido}, ${paciente.nombre}` : `Paciente #${hc.idPaciente}`);
  const pacDni = hc.dniPaciente || paciente?.dni || '—';

  const evolHtml = (hc.evoluciones || []).length === 0
    ? `<div class="empty-state" style="padding:32px"><svg width="36" height="36" viewBox="0 0 24 24" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" stroke-width="2"/></svg><h3>Sin evoluciones</h3><p>Registrá la primera evolución.</p></div>`
    : (hc.evoluciones || []).map(ev => renderEvolucion(ev, canEdit)).join('');

  container.innerHTML = `
    <div class="card" style="margin-bottom:16px;">
      <div class="card-header">
        <div>
          <div class="card-title">HC — ${pacNombre}</div>
          <div class="card-subtitle">Nro. Historia: ${hc.idHistoriaClinica} | Doc: ${pacDni}</div>
        </div>
        ${canEdit ? `<button class="btn btn-primary btn-sm" onclick="abrirNuevaEvolucion(${hc.idHistoriaClinica})">+ Evolución</button>` : ''}
      </div>
    </div>
    <div id="evoluciones-list">${evolHtml}</div>
  `;
}

function renderAdjuntosList(adjuntos) {
  if (!adjuntos || adjuntos.length === 0) return '';
  return `<div style="margin-top:8px;">
    ${adjuntos.map(a => `
      <button type="button" class="btn-icon" title="Descargar ${a.nombreArchivo}" onclick="descargarAdjuntoArchivo(${a.idAdjunto}, '${(a.nombreArchivo || '').replace(/'/g, "\\'")}')" style="display:inline-flex;align-items:center;gap:4px;width:auto;padding:2px 8px;">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" stroke="currentColor" stroke-width="2"/><polyline points="7 10 12 15 17 10" stroke="currentColor" stroke-width="2"/><line x1="12" y1="15" x2="12" y2="3" stroke="currentColor" stroke-width="2"/></svg>
        <span class="text-sm">${a.nombreArchivo}</span>
      </button>`).join('')}
  </div>`;
}

function renderEvolucion(ev, canEdit) {
  const rol = getRol();
  // RF-H3: ADMINISTRATIVO y MEDICO pueden adjuntar estudios; GERENTE no accede a HC.
  const canAttach = rol === 'MEDICO' || rol === 'ADMINISTRATIVO';
  const fecha = ev.fechaHora
    ? new Date(ev.fechaHora).toLocaleString('es-AR', { dateStyle:'medium', timeStyle:'short' })
    : '—';
  const anulada = ev.estado === 'ANULADA';
  const btnAdjuntar = canAttach && !anulada
    ? `<button class="btn-icon" title="Adjuntos" aria-label="Adjuntos" onclick="abrirAdjuntosEvolucion(${JSON.stringify(ev).replace(/"/g,'&quot;')})">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" stroke="currentColor" stroke-width="2"/></svg>
      </button>`
    : '';
  return `
    <div class="card" style="margin-bottom:12px;${anulada ? 'opacity:.6;border-left:3px solid var(--rojo);' : ''}">
      <div style="display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:12px;">
        <div>
          <strong style="color:var(--azul)">${ev.motivoConsulta || 'Consulta'}</strong>
          <span class="text-muted text-sm" style="margin-left:10px;">${fecha}</span>
          ${anulada ? '<span class="badge badge-anulada" style="margin-left:8px;">ANULADA</span>' : ''}
        </div>
        ${(btnAdjuntar || (canEdit && !anulada)) ? `
          <div class="table-actions">
            ${canEdit && !anulada ? `
            <button class="btn-icon" title="Editar" onclick="editarEvolucion(${JSON.stringify(ev).replace(/"/g,'&quot;')})">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2"/></svg>
            </button>
            <button class="btn-icon" title="Anular" onclick="anularEvolucion(${ev.idEvolucion})" style="color:var(--rojo)">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><polyline points="3 6 5 6 21 6" stroke="currentColor" stroke-width="2"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" stroke="currentColor" stroke-width="2"/></svg>
            </button>` : ''}
            ${btnAdjuntar}
          </div>` : ''}
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">
        ${ev.diagnostico ? `<div><span class="text-muted text-sm">Diagnóstico</span><p style="margin-top:2px">${ev.diagnostico}</p></div>` : ''}
        ${ev.tratamiento ? `<div><span class="text-muted text-sm">Tratamiento</span><p style="margin-top:2px">${ev.tratamiento}</p></div>` : ''}
        ${ev.indicaciones ? `<div><span class="text-muted text-sm">Indicaciones</span><p style="margin-top:2px">${ev.indicaciones}</p></div>` : ''}
        ${ev.estudiosSolicitados ? `<div><span class="text-muted text-sm">Estudios</span><p style="margin-top:2px">${ev.estudiosSolicitados}</p></div>` : ''}
      </div>
      ${ev.observaciones ? `<p class="text-muted text-sm" style="margin-top:8px;padding-top:8px;border-top:1px solid var(--gris-borde)">${ev.observaciones}</p>` : ''}
      ${renderAdjuntosList(ev.adjuntos)}
    </div>`;
}

async function descargarAdjuntoArchivo(idAdjunto, nombreArchivo) {
  try {
    const blob = await Api.descargarAdjunto(idAdjunto);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = nombreArchivo || 'adjunto';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  } catch (e) {
    Toast.error(e.message);
  }
}

function abrirNuevaEvolucion(idHC) {
  currentEvolucionId = null;
  evolucionAdjuntosActuales = [];
  document.getElementById('modal-evolucion-title').textContent = 'Nueva Evolución';
  document.getElementById('evol-id-hc').value = idHC;
  document.getElementById('evol-id').value = '';
  ['evol-motivo','evol-diagnostico','evol-tratamiento','evol-indicaciones','evol-estudios','evol-observaciones'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.value = '';
  });
  // Los adjuntos requieren una evolución ya persistida (FK ID_Evolucion NOT NULL) —
  // no se pueden subir hasta guardar por primera vez.
  document.getElementById('evol-adjuntos-group').classList.add('hidden');
  document.getElementById('evol-adjuntos-list').innerHTML = '';
  openModal('modal-evolucion');
}

function editarEvolucion(ev) {
  currentEvolucionId = ev.idEvolucion;
  evolucionAdjuntosActuales = ev.adjuntos || [];
  document.getElementById('modal-evolucion-title').textContent = 'Editar Evolución';
  document.getElementById('evol-id-hc').value     = currentHC?.idHistoriaClinica || '';
  document.getElementById('evol-id').value        = ev.idEvolucion;
  document.getElementById('evol-motivo').value    = ev.motivoConsulta || '';
  document.getElementById('evol-diagnostico').value = ev.diagnostico || '';
  document.getElementById('evol-tratamiento').value = ev.tratamiento || '';
  document.getElementById('evol-indicaciones').value = ev.indicaciones || '';
  document.getElementById('evol-estudios').value   = ev.estudiosSolicitados || '';
  document.getElementById('evol-observaciones').value = ev.observaciones || '';
  document.getElementById('evol-adjuntos-group').classList.remove('hidden');
  document.getElementById('evol-adjunto-file').value = '';
  renderAdjuntosEnModal();
  openModal('modal-evolucion');
}

function renderAdjuntosEnModal() {
  const container = document.getElementById('evol-adjuntos-list');
  container.innerHTML = evolucionAdjuntosActuales.length
    ? renderAdjuntosList(evolucionAdjuntosActuales)
    : '<p class="text-muted text-sm">Sin adjuntos todavía.</p>';
}

// ---- Modal de adjuntos (adjuntar/descargar sin tocar el texto clínico) ----
function abrirAdjuntosEvolucion(ev) {
  currentEvolucionId = ev.idEvolucion;
  evolucionAdjuntosActuales = ev.adjuntos || [];
  document.getElementById('adj-file').value = '';
  renderAdjuntosModal();
  openModal('modal-adjuntos');
}

function renderAdjuntosModal() {
  const container = document.getElementById('adj-lista');
  container.innerHTML = evolucionAdjuntosActuales.length
    ? renderAdjuntosList(evolucionAdjuntosActuales)
    : '<p class="text-muted text-sm">Sin adjuntos todavía.</p>';
}

async function subirAdjuntoModal() {
  const input = document.getElementById('adj-file');
  const file = input.files[0];
  if (!file) { Toast.error('Elegí un archivo para subir.'); return; }
  if (!currentEvolucionId) { Toast.error('No se pudo identificar la evolución.'); return; }
  try {
    const nuevoAdjunto = await Api.addAdjunto(currentEvolucionId, file);
    evolucionAdjuntosActuales = [...evolucionAdjuntosActuales, nuevoAdjunto];
    renderAdjuntosModal();
    input.value = '';
    Toast.success('Adjunto subido.');
  } catch (e) {
    Toast.error(e.message);
  }
}

function cerrarModalAdjuntos() {
  closeModal('modal-adjuntos');
  if (currentHC) verHistoriaClinica(currentHC.idPaciente);
}

async function subirAdjunto() {
  const input = document.getElementById('evol-adjunto-file');
  const file = input.files[0];
  if (!file) {
    Toast.error('Elegí un archivo para subir.');
    return;
  }
  if (!currentEvolucionId) {
    Toast.error('Guardá la evolución antes de adjuntar archivos.');
    return;
  }
  try {
    const nuevoAdjunto = await Api.addAdjunto(currentEvolucionId, file);
    evolucionAdjuntosActuales = [...evolucionAdjuntosActuales, nuevoAdjunto];
    renderAdjuntosEnModal();
    input.value = '';
    Toast.success('Adjunto subido.');
  } catch (e) {
    Toast.error(e.message);
  }
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
