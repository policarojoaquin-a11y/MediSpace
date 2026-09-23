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

    // RF-T8: cancelar el día del médico (vacaciones/licencia). GERENTE/ADMINISTRATIVO sobre
    // cualquier médico; un MEDICO solo sobre sí mismo (el backend fuerza su propio id).
    const puedeCancelarDia = ['GERENTE', 'ADMINISTRATIVO', 'MEDICO'].includes(getRol());

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
          <td class="turno-col-accion"><div class="turno-acciones">${buildTurnoAccionesCompacto(t)}</div></td>
        </tr>`;
    }).join('') || `<tr><td colspan="5" style="color:var(--gris-medio);text-align:center;">Sin turnos visibles</td></tr>`;

    return `
      <div class="turno-column ${completa ? 'completa' : ''}">
        <div class="turno-column-header">
          <div class="turno-column-header-top">
            <div class="turno-column-title">${titulo}</div>
            ${puedeCancelarDia ? `<button class="btn-icon" title="Cancelar día del médico" aria-label="Cancelar día del médico" onclick="abrirCancelarDia(${turno.idMedico})" style="color:var(--rojo)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M21 8V6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h7" stroke="currentColor" stroke-width="2"/><line x1="3" y1="10" x2="21" y2="10" stroke="currentColor" stroke-width="2"/><line x1="16" y1="15" x2="22" y2="21" stroke="currentColor" stroke-width="2"/><line x1="22" y1="15" x2="16" y2="21" stroke="currentColor" stroke-width="2"/></svg>
            </button>` : ''}
          </div>
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
    return `<button class="btn-icon" title="Reservar" onclick="abrirReservaTurno(${t.idTurno}, ${t.idMedico})" style="color:var(--azul);border-color:var(--azul-light)">
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><line x1="12" y1="5" x2="12" y2="19" stroke="currentColor" stroke-width="2"/><line x1="5" y1="12" x2="19" y2="12" stroke="currentColor" stroke-width="2"/></svg>
    </button>`;
  }
  // Botón de cancelar reutilizable — disponible en RESERVADO y EN_ESPERA (RF-T3 / RN-004:
  // solo ATENDIDO es inmutable). Antes faltaba en EN_ESPERA (checklist 27/08).
  const btnCancelar = `<button class="btn-icon" title="Cancelar turno" aria-label="Cancelar turno" onclick="cambiarEstado(${t.idTurno},'CANCELADO')" style="color:var(--rojo)">
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><line x1="18" y1="6" x2="6" y2="18" stroke="currentColor" stroke-width="2"/><line x1="6" y1="6" x2="18" y2="18" stroke="currentColor" stroke-width="2"/></svg>
    </button>`;
  if (t.estado === 'RESERVADO') {
    return `<button class="btn-icon" title="Pasar a En Espera" aria-label="Pasar a En Espera" onclick="cambiarEstado(${t.idTurno},'EN_ESPERA')" style="color:var(--naranja);border-color:var(--naranja-light)">
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/><polyline points="12 6 12 12 16 14" stroke="currentColor" stroke-width="2"/></svg>
    </button>
    ${btnCancelar}`;
  }
  if (t.estado === 'EN_ESPERA') {
    // RF-F1: la factura pendiente ya existe desde que el paciente pasó a "En Espera".
    // Se puede cobrar acá mismo, antes de que el médico lo marque "Atendido".
    let btnCobro = '';
    if (t.idFacturacion && t.estadoPagoFacturacion === 'PENDIENTE') {
      btnCobro = `<button class="btn-icon" title="Registrar cobro" aria-label="Registrar cobro" onclick="abrirCobroDesdeTurno(${t.idFacturacion})" style="color:var(--verde-dim);border-color:var(--verde-light)">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><line x1="12" y1="1" x2="12" y2="23" stroke="currentColor" stroke-width="2"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" stroke="currentColor" stroke-width="2"/></svg>
      </button>`;
    } else if (t.estadoPagoFacturacion === 'PAGADO') {
      btnCobro = `<span class="turno-cobrado" title="Cobrado">$✓</span>`;
    }
    return `${btnCobro}
    <button class="btn-icon" title="Marcar Atendido" aria-label="Marcar Atendido" onclick="cambiarEstado(${t.idTurno},'ATENDIDO')" style="color:var(--verde);border-color:var(--verde-light)">
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="currentColor" stroke-width="2"/><polyline points="22 4 12 14.01 9 11.01" stroke="currentColor" stroke-width="2"/></svg>
    </button>
    <button class="btn-icon" title="No Asistió" aria-label="No Asistió" onclick="cambiarEstado(${t.idTurno},'NO_ASISTIO')">
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" stroke-width="2"/><circle cx="8.5" cy="7" r="4" stroke="currentColor" stroke-width="2"/><line x1="18" y1="8" x2="23" y2="13" stroke="currentColor" stroke-width="2"/><line x1="23" y1="8" x2="18" y2="13" stroke="currentColor" stroke-width="2"/></svg>
    </button>
    ${btnCancelar}`;
  }
  return '';
}

// Search live
document.getElementById('search-turnos')?.addEventListener('input', () => renderTurnos(turnosData));

// Reservar turno
async function abrirReservaTurno(idTurno, idMedico) {
  document.getElementById('reserva-id-turno').value = idTurno;
  document.getElementById('reserva-id-paciente').value = '';
  document.getElementById('reserva-paciente-search').value = '';
  document.getElementById('reserva-paciente-resultados').classList.add('hidden');
  document.getElementById('reserva-tipo').value = 'CONSULTA';
  document.getElementById('reserva-metodo').value = 'EFECTIVO';
  document.getElementById('reserva-copago').value = '';
  document.getElementById('reserva-nuevo-paciente-form').classList.add('hidden');
  ['np-nombre', 'np-apellido', 'np-dni', 'np-fnac'].forEach(id => {
    document.getElementById(id).value = '';
  });
  await Promise.all([
    populatePrestacionSelectReserva(idMedico),
    populateObraSocialSelectReserva(idMedico),
  ]);
  // Ambas promesas pueden resolver en cualquier orden — se recalcula una vez más ya con
  // los dos cachés (prestaciones y obras sociales) poblados, para no perder el cálculo.
  actualizarCopagoDesdeObraSocial();
  openModal('modal-reservar');
}

let reservaPrestacionesCache = [];
let reservaImporteConsultaMedico = null;
let reservaObrasSocialesCache = [];

const fmtImporteReserva = (v) => '$' + Number(v || 0).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

async function populatePrestacionSelectReserva(idMedico) {
  const sel = document.getElementById('reserva-prestacion');
  sel.innerHTML = '<option value="">Consulta general</option>';
  reservaPrestacionesCache = [];
  reservaImporteConsultaMedico = null;
  actualizarImporteEstimadoReserva();
  if (!idMedico) return;
  try {
    const [prestaciones, medico] = await Promise.all([
      Api.getMedicoPrestaciones(idMedico),
      Api.getMedico(idMedico).catch(() => null),
    ]);
    reservaPrestacionesCache = prestaciones || [];
    reservaImporteConsultaMedico = medico ? medico.importeConsulta : null;
    // "Consulta general" también muestra el importe: el de consulta del médico.
    if (reservaImporteConsultaMedico != null) {
      sel.options[0].textContent = `Consulta general — ${fmtImporteReserva(reservaImporteConsultaMedico)}`;
    }
    reservaPrestacionesCache.forEach(p => {
      const opt = document.createElement('option');
      opt.value = p.idPrestacion;
      opt.textContent = p.importeParticular != null
        ? `${p.nombrePrestacion} — ${fmtImporteReserva(p.importeParticular)}`
        : p.nombrePrestacion;
      sel.appendChild(opt);
    });
    actualizarImporteEstimadoReserva();
  } catch {
    // Si falla, se deja solo la opción "Consulta general" — no bloquea la reserva.
  }
}

// Importe estimado de la consulta/prestación elegida — base para el hint de facturación Y
// para calcular cuánto le queda a cobrar al paciente una vez descontada la cobertura de la OS.
function obtenerImporteEstimadoReserva() {
  const idPrestacion = parseInt(document.getElementById('reserva-prestacion').value) || null;
  const p = idPrestacion ? reservaPrestacionesCache.find(x => x.idPrestacion === idPrestacion) : null;
  if (p && p.importeParticular != null) return Number(p.importeParticular);
  if (reservaImporteConsultaMedico != null) return Number(reservaImporteConsultaMedico);
  return null;
}

// El importe de la factura sale del precio de la prestación elegida (o del importe de consulta
// del médico si es "Consulta general" / la prestación no tiene precio). La administración puede
// ajustarlo al registrar el cobro. Si cambia la prestación después de elegir la obra social, el
// monto a cobrar al paciente se recalcula también (depende de ambos).
function actualizarImporteEstimadoReserva() {
  const hint = document.getElementById('reserva-importe-hint');
  const importeEstimado = obtenerImporteEstimadoReserva();
  if (hint) {
    const idPrestacion = parseInt(document.getElementById('reserva-prestacion').value) || null;
    const p = idPrestacion ? reservaPrestacionesCache.find(x => x.idPrestacion === idPrestacion) : null;
    if (p && p.importeParticular != null) {
      hint.textContent = `Se factura ${fmtImporteReserva(p.importeParticular)} (precio de la prestación).`;
    } else if (reservaImporteConsultaMedico != null) {
      hint.textContent = `Se factura ${fmtImporteReserva(reservaImporteConsultaMedico)} (importe de consulta del médico).`;
    } else {
      hint.textContent = '';
    }
  }
  actualizarCopagoDesdeObraSocial();
}

// Dropdown de obra social: solo las que ESE médico acepta, cada una con el coseguro que él
// mismo pactó (cartilla en /medicos/{id}/obras-sociales). Reemplaza el campo de texto libre
// que antes dejaba tipear cualquier nombre sin relación con lo que el médico realmente cobra.
// Ojo con el nombre: "importeCoseguro" es lo que LA OBRA SOCIAL le paga al médico directo (no
// entra a la caja del consultorio) — no lo que paga el paciente. Ver actualizarCopagoDesdeObraSocial.
async function populateObraSocialSelectReserva(idMedico) {
  const sel = document.getElementById('reserva-obra-social');
  sel.innerHTML = '<option value="">Particular (sin obra social)</option>';
  reservaObrasSocialesCache = [];
  if (!idMedico) { actualizarCopagoDesdeObraSocial(); return; }
  try {
    reservaObrasSocialesCache = await Api.getMedicoObrasSociales(idMedico) || [];
  } catch {
    reservaObrasSocialesCache = [];
  }
  reservaObrasSocialesCache.forEach(os => {
    const opt = document.createElement('option');
    opt.value = os.nombreObraSocial;
    opt.textContent = os.importeCoseguro != null
      ? `${os.nombreObraSocial} — Cubre ${fmtImporteReserva(os.importeCoseguro)}`
      : `${os.nombreObraSocial} — cobertura sin definir`;
    sel.appendChild(opt);
  });
  actualizarCopagoDesdeObraSocial();
}

// Monto a cobrar al paciente EN MANO = importe de la consulta menos lo que cubre la obra
// social (RN-025). Si es Particular, paga el importe completo. Si el médico no definió
// cuánto cubre esa obra social, se deja en blanco para cargar a mano (no se puede calcular).
function actualizarCopagoDesdeObraSocial() {
  const nombre = document.getElementById('reserva-obra-social').value;
  const copagoInput = document.getElementById('reserva-copago');
  const hint = document.getElementById('reserva-copago-hint');
  const importeEstimado = obtenerImporteEstimadoReserva();

  if (!nombre) {
    copagoInput.value = importeEstimado != null ? importeEstimado.toFixed(2) : '';
    if (hint) hint.textContent = importeEstimado != null
      ? `Sin obra social: el paciente paga ${fmtImporteReserva(importeEstimado)} en mano.`
      : '';
    return;
  }

  const os = reservaObrasSocialesCache.find(x => x.nombreObraSocial === nombre);
  if (os && os.importeCoseguro != null && importeEstimado != null) {
    const aCobrar = Math.max(0, importeEstimado - Number(os.importeCoseguro));
    copagoInput.value = aCobrar.toFixed(2);
    if (hint) hint.textContent = `${nombre} cubre ${fmtImporteReserva(os.importeCoseguro)} de ${fmtImporteReserva(importeEstimado)} — el paciente paga ${fmtImporteReserva(aCobrar)} en mano.`;
  } else {
    copagoInput.value = '';
    if (hint) hint.textContent = (os && os.importeCoseguro == null)
      ? `El médico no definió cuánto cubre ${nombre} — cargá el monto a cobrar manualmente.`
      : '';
  }
}

function toggleNuevoPacienteReserva() {
  document.getElementById('reserva-nuevo-paciente-form').classList.toggle('hidden');
}

// ---- Buscador de paciente por DNI/nombre (reemplaza el dropdown) ----
function seleccionarPacienteReserva(idPaciente) {
  const p = pacientesCache.find(x => x.idPaciente === idPaciente);
  if (!p) return;
  document.getElementById('reserva-id-paciente').value = p.idPaciente;
  document.getElementById('reserva-paciente-search').value = `${p.apellido}, ${p.nombre} — Doc: ${p.dni}`;
  document.getElementById('reserva-paciente-resultados').classList.add('hidden');

  // Si la obra social del paciente está entre las que el médico acepta, se preselecciona
  // sola y dispara el cálculo del copago — si no, queda en "Particular" para elegir a mano.
  if (p.idObraSocial) {
    const match = reservaObrasSocialesCache.find(os => os.idObraSocial === p.idObraSocial);
    if (match) {
      document.getElementById('reserva-obra-social').value = match.nombreObraSocial;
      actualizarCopagoDesdeObraSocial();
    }
  }
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
          ${p.apellido}, ${p.nombre} <span class="dni">Doc ${p.dni}</span>
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
    Toast.error('Completá nombre, apellido, DNI / Pasaporte y fecha de nacimiento del paciente.');
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
    idPrestacion: parseInt(document.getElementById('reserva-prestacion').value) || null,
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

// ---- RF-F1: cobrar desde el tablero mientras el paciente está en sala de espera ----
async function abrirCobroDesdeTurno(idFacturacion) {
  try {
    const f = await Api.getFacturacion(idFacturacion);
    document.getElementById('cobro-id-factura').value = idFacturacion;
    document.getElementById('cobro-origen').value    = 'turnos';
    document.getElementById('cobro-importe').value    = f.importeTotal || '';
    document.getElementById('cobro-copago').value     = f.importeCopago || '';
    document.getElementById('cobro-metodo').value     = f.metodoPagoPlanificado || 'EFECTIVO';
    recalcularCobroOs();
    openModal('modal-cobro');
  } catch (e) {
    Toast.error(e.message);
  }
}

// ---- RF-T8: Cancelar día del médico (vacaciones / licencia) ----
function abrirCancelarDia(idMedico) {
  const medico = turnosMedicosCache.find(m => m.idMedico === idMedico);
  document.getElementById('cancelar-dia-id-medico').value = idMedico;
  document.getElementById('cancelar-dia-medico-nombre').value =
    medico ? `Dr/a. ${medico.apellido}, ${medico.nombre}` : `Médico #${idMedico}`;
  document.getElementById('cancelar-dia-desde').value = fechaISO(turnosFechaActual);
  document.getElementById('cancelar-dia-hasta').value = '';
  document.getElementById('cancelar-dia-motivo').value = '';
  document.getElementById('cancelar-dia-contactar').innerHTML = '';
  openModal('modal-cancelar-dia');
}

async function confirmarCancelarDia() {
  const idMedico = parseInt(document.getElementById('cancelar-dia-id-medico').value);
  const fecha = document.getElementById('cancelar-dia-desde').value;
  const fechaHasta = document.getElementById('cancelar-dia-hasta').value || null;
  const motivo = document.getElementById('cancelar-dia-motivo').value.trim() || null;
  if (!fecha) { Toast.error('Elegí la fecha "Desde".'); return; }
  if (fechaHasta && fechaHasta < fecha) { Toast.error('El "Hasta" no puede ser anterior al "Desde".'); return; }
  if (!confirm('¿Cancelar los turnos de ese médico en el período seleccionado?')) return;

  const btn = document.getElementById('btn-confirmar-cancelar-dia');
  btn.disabled = true;
  try {
    const r = await Api.cancelarDiaMedico({ idMedico, fecha, fechaHasta, motivo });
    Toast.success(`Se cancelaron ${r.turnosCancelados} turno(s): ${r.disponiblesCancelados} disponible(s), ${r.reservadosCancelados} con paciente.`);

    const cont = document.getElementById('cancelar-dia-contactar');
    const pacientes = r.pacientesAContactar || [];
    if (pacientes.length) {
      cont.innerHTML = `
        <div class="alert alert-warning" style="font-size:13px;">
          <strong>Contactá a estos pacientes para reprogramar:</strong>
          <ul class="cancelar-dia-lista">
            ${pacientes.map(p => `<li>${p.nombrePaciente} — ${p.telefonoPaciente || 'sin teléfono'} — ${new Date(p.fechaHora).toLocaleString('es-AR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}</li>`).join('')}
          </ul>
        </div>`;
    } else {
      cont.innerHTML = '';
      closeModal('modal-cancelar-dia');
    }
    loadTurnos();
  } catch (e) {
    Toast.error(e.message);
  } finally {
    btn.disabled = false;
  }
}
