// ==================== TURNOS MODULE ====================
let turnosData = [];
let pacientesCache = [];
let turnosMedicosCache = [];
let turnosFiltroCheckboxList = null;
let turnosFechaActual = new Date();

const ESTADO_BADGE = {
  DISPONIBLE:  'badge-disponible',
  RESERVADO:   'badge-reservado',
  EN_ESPERA:   'badge-en-espera',
  ATENDIDO:    'badge-atendido',
  CANCELADO:   'badge-cancelado',
  NO_ASISTIO:  'badge-cancelado',
};

function badgeTurno(estado) {
  return `<span class="badge ${ESTADO_BADGE[estado] || 'badge-neutral'}">${estado.replace('_',' ')}</span>`;
}

function fechaISO(date) {
  return date.toISOString().split('T')[0];
}

function actualizarFechaLabel() {
  const d = turnosFechaActual;
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const yyyy = d.getFullYear();
  const label = document.getElementById('turnos-fecha-label');
  if (label) label.textContent = `TURNOS DEL DÍA ${dd}-${mm}-${yyyy}`;
  const input = document.getElementById('turnos-fecha-input');
  if (input) input.value = `${yyyy}-${mm}-${dd}`;
}

async function populateTurnosFiltroMedicos() {
  if (!turnosMedicosCache.length) {
    turnosMedicosCache = await Api.getMedicos().catch(() => []);
  }
  const container = document.getElementById('turnos-filtro-medicos');
  if (!container) return;
  const items = turnosMedicosCache.map(m => ({ idMedico: m.idMedico, label: `Dr/a. ${m.apellido}, ${m.nombre}` }));
  // Por default, todos los médicos están seleccionados.
  const yaExistia = !!turnosFiltroCheckboxList;
  const selectedIds = yaExistia ? turnosFiltroCheckboxList.getSelectedIds() : turnosMedicosCache.map(m => m.idMedico);
  turnosFiltroCheckboxList = renderCheckboxList(container, items, { idKey: 'idMedico', labelKey: 'label', selectedIds });
  actualizarResumenMedicosFiltro();
}

function actualizarResumenMedicosFiltro() {
  const resumen = document.getElementById('turnos-filtro-medicos-resumen');
  if (!resumen || !turnosFiltroCheckboxList) return;
  const seleccionados = turnosFiltroCheckboxList.getSelectedIds();
  const total = turnosMedicosCache.length;
  resumen.textContent = (total === 0 || seleccionados.length === total)
    ? 'Todos los médicos'
    : seleccionados.length === 0
      ? 'Ningún médico'
      : `${seleccionados.length} de ${total} médicos`;
}

// ---- Popover del filtro de médicos: compacto, se abre/cierra sin ocupar layout fijo ----
document.getElementById('btn-turnos-filtro-medicos')?.addEventListener('click', (e) => {
  e.stopPropagation();
  document.getElementById('turnos-medico-popover')?.classList.toggle('hidden');
});
document.addEventListener('click', (e) => {
  const wrapper = document.getElementById('turnos-medico-filtro-wrapper');
  const popover = document.getElementById('turnos-medico-popover');
  if (!wrapper || !popover || popover.classList.contains('hidden')) return;
  if (!wrapper.contains(e.target)) popover.classList.add('hidden');
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') document.getElementById('turnos-medico-popover')?.classList.add('hidden');
});

async function loadTurnos() {
  const board = document.getElementById('turnos-board');
  actualizarFechaLabel();
  board.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div>`;
  try {
    await populateTurnosFiltroMedicos();
    const idsMedico = turnosFiltroCheckboxList.getSelectedIds();

    turnosData = await Api.getTurnos({ fecha: fechaISO(turnosFechaActual), idsMedico });
    renderTurnos(turnosData);

    // Cache de pacientes para el buscador de la reserva
    if (!pacientesCache.length) {
      pacientesCache = await Api.getPacientes().catch(() => []);
    }
  } catch (e) {
    board.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

// Navegación de fecha
document.getElementById('btn-turno-hoy')?.addEventListener('click', () => {
  turnosFechaActual = new Date();
  loadTurnos();
});
document.getElementById('btn-turno-dia-anterior')?.addEventListener('click', () => {
  turnosFechaActual = new Date(turnosFechaActual.getTime() - 86400000);
  loadTurnos();
});
document.getElementById('btn-turno-dia-siguiente')?.addEventListener('click', () => {
  turnosFechaActual = new Date(turnosFechaActual.getTime() + 86400000);
  loadTurnos();
});

// Calendario: elegir cualquier fecha directamente
document.getElementById('btn-turno-calendario')?.addEventListener('click', () => {
  const input = document.getElementById('turnos-fecha-input');
  if (input?.showPicker) input.showPicker();
});
document.getElementById('turnos-fecha-input')?.addEventListener('change', (e) => {
  if (!e.target.value) return;
  const [y, m, d] = e.target.value.split('-').map(Number);
  turnosFechaActual = new Date(y, m - 1, d);
  loadTurnos();
});

// Filtro por médico: se combina con la fecha sin resetearse, filtra en vivo (sin botón "Aplicar")
document.getElementById('turnos-filtro-medicos')?.addEventListener('change', () => {
  actualizarResumenMedicosFiltro();
  loadTurnos();
});

// Tablero de columnas: una columna por médico que tiene agenda ese día (turnos
// generados), entre los médicos seleccionados en el filtro. El backend ya filtra
// por idsMedico, así que agrupar directamente sobre los datos devueltos alcanza:
// un médico seleccionado pero sin turnos ese día no muestra columna.
function renderTurnos(data) {
  const board = document.getElementById('turnos-board');
  const q = document.getElementById('search-turnos')?.value.trim().toLowerCase() || '';

  if (!data || data.length === 0) {
    board.innerHTML = `<div class="empty-state"><svg width="40" height="40" viewBox="0 0 24 24" fill="none"><rect x="3" y="4" width="18" height="18" rx="2" ry="2" stroke="currentColor" stroke-width="2"/><line x1="3" y1="10" x2="21" y2="10" stroke="currentColor" stroke-width="2"/></svg><h3>Sin turnos</h3><p>No hay turnos para los médicos y la fecha seleccionados.</p></div>`;
    return;
  }

  // Agrupar por médico, preservando el orden de aparición
  const grupos = new Map();
  data.forEach(t => {
    const key = t.idMedico;
    if (!grupos.has(key)) {
      const medico = turnosMedicosCache.find(m => m.idMedico === t.idMedico);
      grupos.set(key, { turno: t, medico, turnos: [] });
    }
    grupos.get(key).turnos.push(t);
  });

  board.innerHTML = Array.from(grupos.values()).map(({ turno, medico, turnos: turnosGrupo }) => {
    const turnos = [...turnosGrupo].sort((a, b) => new Date(a.fechaHora) - new Date(b.fechaHora));
    const disponibles = turnos.filter(t => t.estado === 'DISPONIBLE').length;
    const total = turnos.length;
    const ocupados = total - disponibles;
    const completa = disponibles === 0;

    // Formato defensivo: sin especialidad no debe quedar una coma huérfana pegada al pipe.
    const numeroConsultorio = turno.numeroConsultorio || '—';
    const especialidad = (turno.nombreEspecialidad || '').toUpperCase();
    const nombreCol = medico ? `${medico.apellido} ${medico.nombre}` : (turno.nombreMedico || `Médico #${turno.idMedico}`);
    const titulo = especialidad
      ? `${numeroConsultorio} | ${especialidad}, ${nombreCol}`
      : `${numeroConsultorio} | ${nombreCol}`;

    const filas = turnos.map(t => {
      const hora = t.fechaHora ? new Date(t.fechaHora).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false }) : '—';
      const estadoClase = 'turno-row-' + t.estado.toLowerCase().replace('_', '-');
      const matchQuery = q && `${t.nombrePaciente || ''}`.toLowerCase().includes(q);
      const dimQuery = q && !matchQuery && t.nombrePaciente;
      const rowClass = `${estadoClase}${matchQuery ? ' turno-row-highlight' : ''}${dimQuery ? ' turno-row-dim' : ''}`;
      return `
        <tr class="${rowClass}" title="${t.estado.replace('_', ' ')}">
          <td class="turno-hora">${hora}</td>
          <td><span class="turno-estado-dot"></span></td>
          <td>${t.nombrePaciente || '<span class="text-muted">Disponible</span>'}</td>
          <td>${t.telefonoPaciente || '—'}</td>
          <td class="turno-col-accion">${buildTurnoAccionesCompacto(t)}</td>
        </tr>`;
    }).join('') || `<tr><td colspan="5" style="color:var(--gris-medio);text-align:center;">Sin turnos visibles</td></tr>`;

    return `
      <div class="turno-column ${completa ? 'completa' : ''}">
        <div class="turno-column-header">
          <div class="turno-column-title">${titulo}</div>
          <div class="turno-column-stats">${disponibles} disponibles · ${ocupados}/${total}</div>
        </div>
        <div class="turno-column-body">
          <table class="turno-column-table">
            <colgroup><col class="col-hora"><col class="col-dot"><col class="col-paciente"><col class="col-telefono"><col class="col-accion"></colgroup>
            <thead><tr><th>Hora</th><th></th><th>Paciente</th><th>Teléfono</th><th></th></tr></thead>
            <tbody>${filas}</tbody>
          </table>
        </div>
      </div>`;
  }).join('');
}

// Acción compacta (íconos) para las columnas angostas del tablero de turnos
function buildTurnoAccionesCompacto(t) {
  if (t.estado === 'DISPONIBLE') {
    return `<button class="btn-icon" title="Reservar" onclick="abrirReservaTurno(${t.idTurno})" style="color:var(--azul);border-color:var(--azul-light)">
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><line x1="12" y1="5" x2="12" y2="19" stroke="currentColor" stroke-width="2"/><line x1="5" y1="12" x2="19" y2="12" stroke="currentColor" stroke-width="2"/></svg>
    </button>`;
  }
  if (t.estado === 'RESERVADO') {
    return `<button class="btn-icon" title="Pasar a En Espera" onclick="cambiarEstado(${t.idTurno},'EN_ESPERA')" style="color:var(--naranja);border-color:var(--naranja-light)">
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/><polyline points="12 6 12 12 16 14" stroke="currentColor" stroke-width="2"/></svg>
    </button>
    <button class="btn-icon" title="Cancelar" onclick="cambiarEstado(${t.idTurno},'CANCELADO')" style="color:var(--rojo)">
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><line x1="18" y1="6" x2="6" y2="18" stroke="currentColor" stroke-width="2"/><line x1="6" y1="6" x2="18" y2="18" stroke="currentColor" stroke-width="2"/></svg>
    </button>`;
  }
  if (t.estado === 'EN_ESPERA') {
    return `<button class="btn-icon" title="Marcar Atendido" onclick="cambiarEstado(${t.idTurno},'ATENDIDO')" style="color:var(--verde);border-color:var(--verde-light)">
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="currentColor" stroke-width="2"/><polyline points="22 4 12 14.01 9 11.01" stroke="currentColor" stroke-width="2"/></svg>
    </button>
    <button class="btn-icon" title="No Asistió" onclick="cambiarEstado(${t.idTurno},'NO_ASISTIO')">
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" stroke-width="2"/><circle cx="8.5" cy="7" r="4" stroke="currentColor" stroke-width="2"/><line x1="18" y1="8" x2="23" y2="13" stroke="currentColor" stroke-width="2"/><line x1="23" y1="8" x2="18" y2="13" stroke="currentColor" stroke-width="2"/></svg>
    </button>`;
  }
  return '';
}

// Search live
document.getElementById('search-turnos')?.addEventListener('input', () => renderTurnos(turnosData));

// Reservar turno
function abrirReservaTurno(idTurno) {
  document.getElementById('reserva-id-turno').value = idTurno;
  document.getElementById('reserva-id-paciente').value = '';
  document.getElementById('reserva-paciente-search').value = '';
  document.getElementById('reserva-paciente-resultados').classList.add('hidden');
  document.getElementById('reserva-tipo').value = 'CONSULTA';
  document.getElementById('reserva-metodo').value = 'EFECTIVO';
  document.getElementById('reserva-obra-social').value = '';
  document.getElementById('reserva-copago').value = '';
  document.getElementById('reserva-nuevo-paciente-form').classList.add('hidden');
  ['np-nombre', 'np-apellido', 'np-dni', 'np-fnac'].forEach(id => {
    document.getElementById(id).value = '';
  });
  openModal('modal-reservar');
}

function toggleNuevoPacienteReserva() {
  document.getElementById('reserva-nuevo-paciente-form').classList.toggle('hidden');
}

// ---- Buscador de paciente por DNI/nombre (reemplaza el dropdown) ----
function seleccionarPacienteReserva(idPaciente) {
  const p = pacientesCache.find(x => x.idPaciente === idPaciente);
  if (!p) return;
  document.getElementById('reserva-id-paciente').value = p.idPaciente;
  document.getElementById('reserva-paciente-search').value = `${p.apellido}, ${p.nombre} — DNI: ${p.dni}`;
  document.getElementById('reserva-paciente-resultados').classList.add('hidden');
}

document.getElementById('reserva-paciente-search')?.addEventListener('input', (e) => {
  const q = e.target.value.trim().toLowerCase();
  const resultados = document.getElementById('reserva-paciente-resultados');
  // Cambiar el texto de búsqueda invalida la selección previa
  document.getElementById('reserva-id-paciente').value = '';

  if (!q) { resultados.classList.add('hidden'); resultados.innerHTML = ''; return; }

  const matches = pacientesCache.filter(p =>
    (p.dni || '').toLowerCase().includes(q) ||
    `${p.nombre} ${p.apellido}`.toLowerCase().includes(q)
  ).slice(0, 8);

  resultados.innerHTML = matches.length
    ? matches.map(p => `
        <div class="patient-search-item" onmousedown="seleccionarPacienteReserva(${p.idPaciente})">
          ${p.apellido}, ${p.nombre} <span class="dni">DNI ${p.dni}</span>
        </div>`).join('')
    : `<div class="patient-search-empty">Sin resultados. Podés crear un paciente nuevo.</div>`;
  resultados.classList.remove('hidden');
});

document.getElementById('reserva-paciente-search')?.addEventListener('blur', () => {
  setTimeout(() => document.getElementById('reserva-paciente-resultados').classList.add('hidden'), 150);
});

async function crearPacienteRapido() {
  const payload = {
    nombre: document.getElementById('np-nombre').value.trim(),
    apellido: document.getElementById('np-apellido').value.trim(),
    dni: document.getElementById('np-dni').value.trim(),
    fechaNacimiento: document.getElementById('np-fnac').value,
  };
  if (!payload.nombre || !payload.apellido || !payload.dni || !payload.fechaNacimiento) {
    Toast.error('Completá nombre, apellido, DNI y fecha de nacimiento del paciente.');
    return;
  }
  try {
    const nuevoPaciente = await Api.createPaciente(payload);
    pacientesCache.push(nuevoPaciente);
    seleccionarPacienteReserva(nuevoPaciente.idPaciente);
    document.getElementById('reserva-nuevo-paciente-form').classList.add('hidden');
    Toast.success('Paciente creado y seleccionado.');
  } catch (e) {
    Toast.error(e.message);
  }
}

async function confirmarReserva() {
  const idTurno    = document.getElementById('reserva-id-turno').value;
  const idPaciente = document.getElementById('reserva-id-paciente').value;
  if (!idPaciente) { Toast.error('Seleccioná un paciente.'); return; }

  const payload = {
    idPaciente:   parseInt(idPaciente),
    tipoConsulta: document.getElementById('reserva-tipo').value,
    metodoPago:   document.getElementById('reserva-metodo').value,
    obraSocial:   document.getElementById('reserva-obra-social').value,
    copago:       parseFloat(document.getElementById('reserva-copago').value) || 0,
  };
  try {
    await Api.reservarTurno(idTurno, payload);
    Toast.success('Turno reservado correctamente.');
    closeModal('modal-reservar');
    loadTurnos();
  } catch (e) {
    Toast.error(e.message);
  }
}

async function cambiarEstado(idTurno, nuevoEstado) {
  const confirmar = nuevoEstado === 'CANCELADO'
    ? confirm('¿Cancelar este turno?')
    : nuevoEstado === 'NO_ASISTIO'
      ? confirm('¿Marcar como No Asistió?')
      : true;
  if (!confirmar) return;
  try {
    await Api.cambiarEstadoTurno(idTurno, nuevoEstado);
    const msgs = { ATENDIDO:'Turno marcado como Atendido. Se generó factura automáticamente.', CANCELADO:'Turno cancelado.', EN_ESPERA:'Paciente marcado como En Espera.', NO_ASISTIO:'Turno registrado como No Asistió.' };
    Toast.success(msgs[nuevoEstado] || 'Estado actualizado.');
    loadTurnos();
  } catch (e) {
    Toast.error(e.message);
  }
}
