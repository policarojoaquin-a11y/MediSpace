// ==================== FACTURACIÓN MODULE ====================
let facturacionData = [];              // crudo del server (ya acotado por fecha)
let facturacionFecha = new Date();     // día seleccionado (modo día)
let facturacionRango = null;           // { desde, hasta } en modo rango; null = modo día
let facturacionVerTodas = false;       // true = sin filtro de fecha

const ESTADO_PAGO_BADGE = {
  PENDIENTE:   'badge-pendiente',
  PAGADO:      'badge-pagado',
  PARCIAL:     'badge-reservado',
  ANULADO:     'badge-anulada',
  REINTEGRADO: 'badge-neutral',
};

const METODO_LABEL = { EFECTIVO: 'Efectivo', TRANSFERENCIA: 'Transferencia', PENDIENTE: '—' };

function factFechaISO(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function factFechaDMY(iso) { const [y, m, d] = iso.split('-'); return `${d}-${m}-${y}`; }

function actualizarFactLabel() {
  const label = document.getElementById('fact-fecha-label');
  if (!label) return;
  if (facturacionVerTodas) {
    label.textContent = 'TODAS LAS FACTURACIONES';
  } else if (facturacionRango) {
    label.textContent = `${factFechaDMY(facturacionRango.desde)} → ${factFechaDMY(facturacionRango.hasta)}`;
  } else {
    label.textContent = `FACTURACIÓN DEL ${factFechaDMY(factFechaISO(facturacionFecha))}`;
    const input = document.getElementById('fact-fecha-input');
    if (input) input.value = factFechaISO(facturacionFecha);
  }
}

// El estado que se muestra/filtra: una transferencia registrada pero sin validar es un
// sub-estado de PENDIENTE que el resto de la UI ya distingue por metodoPago.
function estadoEfectivoFactura(f) {
  return (f.estadoPago === 'PENDIENTE' && f.metodoPago === 'TRANSFERENCIA')
    ? 'TRANSFERENCIA_A_VALIDAR'
    : f.estadoPago;
}

async function loadFacturacion() {
  const tbody = document.getElementById('tabla-facturacion');
  actualizarFactLabel();
  tbody.innerHTML = `<tr><td colspan="9"><div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div></td></tr>`;
  try {
    let params = {};
    if (facturacionVerTodas)      params = {};
    else if (facturacionRango)    params = { desde: facturacionRango.desde, hasta: facturacionRango.hasta };
    else                          params = { desde: factFechaISO(facturacionFecha) };
    facturacionData = await Api.getFacturaciones(params);
    aplicarFiltrosFacturacion();
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="9"><div class="alert alert-error">${e.message}</div></td></tr>`;
  }
}

// Filtros client-side (estado + texto) sobre lo que ya trajo el server acotado por fecha.
function aplicarFiltrosFacturacion() {
  const q = (document.getElementById('search-facturacion')?.value || '').toLowerCase().trim();
  const estado = document.getElementById('fact-estado-filtro')?.value || '';
  let filtradas = facturacionData;
  if (estado) filtradas = filtradas.filter(f => estadoEfectivoFactura(f) === estado);
  if (q) filtradas = filtradas.filter(f => `${f.nombrePaciente || ''} ${f.nombreMedico || ''}`.toLowerCase().includes(q));

  const resumen = document.getElementById('fact-resumen');
  if (resumen) {
    resumen.textContent = (filtradas.length === facturacionData.length)
      ? `${facturacionData.length} facturaciones`
      : `${filtradas.length} de ${facturacionData.length}`;
  }
  renderFacturacion(filtradas);
}

function renderFacturacion(data) {
  const tbody = document.getElementById('tabla-facturacion');
  if (!data || data.length === 0) {
    const sub = facturacionVerTodas
      ? 'No hay facturaciones registradas.'
      : 'Probá con otra fecha, un rango, o "Ver todas".';
    tbody.innerHTML = `<tr><td colspan="9"><div class="empty-state"><svg width="40" height="40" viewBox="0 0 24 24" fill="none"><line x1="12" y1="1" x2="12" y2="23" stroke="currentColor" stroke-width="2"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" stroke="currentColor" stroke-width="2"/></svg><h3>Sin facturaciones para este filtro</h3><p>${sub}</p></div></td></tr>`;
    return;
  }
  tbody.innerHTML = data.map(f => {
    const fecha = f.fechaFacturacion ? new Date(f.fechaFacturacion).toLocaleDateString('es-AR') : '—';
    // Un cobro por transferencia ya registrado deja metodoPago = 'TRANSFERENCIA' y estadoPago
    // = 'PENDIENTE' (a validar). Sin cobro todavía, metodoPago = 'PENDIENTE'.
    const transferenciaAValidar = f.estadoPago === 'PENDIENTE' && f.metodoPago === 'TRANSFERENCIA';
    const badge = transferenciaAValidar
      ? `<span class="badge badge-en-espera">TRANSFERENCIA A VALIDAR</span>`
      : `<span class="badge ${ESTADO_PAGO_BADGE[f.estadoPago] || 'badge-neutral'}">${f.estadoPago}</span>`;
    const metodo = METODO_LABEL[f.metodoPago] || f.metodoPago || '—';
    const metodoPlanificado = f.metodoPagoPlanificado || 'EFECTIVO';
    const copagoPlanificado = f.importeCopago || 0;
    let acciones = '—';
    if (transferenciaAValidar) {
      acciones = `<button class="btn btn-sm" style="background:var(--naranja);color:#fff;border:none;cursor:pointer;" onclick="confirmarTransferenciaCobro(${f.idFacturacion})">Confirmar transferencia</button>`;
    } else if (f.estadoPago === 'PENDIENTE') {
      acciones = `<button class="btn btn-success btn-sm" onclick="abrirCobro(${f.idFacturacion}, ${f.importeTotal}, '${metodoPlanificado}', ${copagoPlanificado})">Registrar Cobro</button>`;
    }
    return `
      <tr>
        <td>${fecha}</td>
        <td>${f.nombrePaciente || '—'}</td>
        <td>${f.nombreMedico || '—'}</td>
        <td><strong>$${Number(f.importeTotal || 0).toLocaleString('es-AR', {minimumFractionDigits:2})}</strong></td>
        <td>$${Number(f.importeCubiertoOs || 0).toLocaleString('es-AR', {minimumFractionDigits:2})}</td>
        <td>$${Number(f.importeCopago || 0).toLocaleString('es-AR', {minimumFractionDigits:2})}</td>
        <td>${metodo}</td>
        <td>${badge}</td>
        <td>${acciones}</td>
      </tr>`;
  }).join('');
}

async function confirmarTransferenciaCobro(idFactura) {
  if (!confirm('¿Confirmar que la transferencia fue recibida? La factura pasará a PAGADO.')) return;
  try {
    await Api.confirmarTransferencia(idFactura);
    Toast.success('Transferencia confirmada. Factura marcada como PAGADO.');
    loadFacturacion();
  } catch (e) {
    Toast.error(e.message);
  }
}

// Search + filtro de estado (client-side, combinables)
document.getElementById('search-facturacion')?.addEventListener('input', aplicarFiltrosFacturacion);
document.getElementById('fact-estado-filtro')?.addEventListener('change', aplicarFiltrosFacturacion);

// ---- Barra de fecha (mismo patrón que Turnos) ----
function factModoDia(nuevaFecha) {
  facturacionFecha = nuevaFecha;
  facturacionRango = null;
  facturacionVerTodas = false;
  document.getElementById('fact-rango-bar')?.classList.add('hidden');
  loadFacturacion();
}
document.getElementById('btn-fact-hoy')?.addEventListener('click', () => factModoDia(new Date()));
document.getElementById('btn-fact-dia-anterior')?.addEventListener('click', () =>
  factModoDia(new Date(facturacionFecha.getTime() - 86400000)));
document.getElementById('btn-fact-dia-siguiente')?.addEventListener('click', () =>
  factModoDia(new Date(facturacionFecha.getTime() + 86400000)));
document.getElementById('btn-fact-calendario')?.addEventListener('click', () => {
  const input = document.getElementById('fact-fecha-input');
  if (input?.showPicker) input.showPicker();
});
document.getElementById('fact-fecha-input')?.addEventListener('change', (e) => {
  if (!e.target.value) return;
  const [y, m, d] = e.target.value.split('-').map(Number);
  factModoDia(new Date(y, m - 1, d));
});
document.getElementById('btn-fact-ver-todas')?.addEventListener('click', () => {
  facturacionVerTodas = true;
  facturacionRango = null;
  document.getElementById('fact-rango-bar')?.classList.add('hidden');
  loadFacturacion();
});
document.getElementById('btn-fact-rango-toggle')?.addEventListener('click', () => {
  const bar = document.getElementById('fact-rango-bar');
  if (!bar) return;
  bar.classList.toggle('hidden');
  if (!bar.classList.contains('hidden')) {
    const desde = document.getElementById('fact-desde');
    const hasta = document.getElementById('fact-hasta');
    if (!desde.value) desde.value = factFechaISO(facturacionFecha);
    if (!hasta.value) hasta.value = factFechaISO(new Date());
  }
});
document.getElementById('btn-fact-rango-aplicar')?.addEventListener('click', () => {
  const desde = document.getElementById('fact-desde').value;
  const hasta = document.getElementById('fact-hasta').value;
  if (!desde || !hasta) { Toast.error('Elegí "Desde" y "Hasta".'); return; }
  if (hasta < desde) { Toast.error('"Hasta" no puede ser anterior a "Desde".'); return; }
  facturacionRango = { desde, hasta };
  facturacionVerTodas = false;
  loadFacturacion();
});

function abrirCobro(idFactura, importeTotal, metodoPlanificado, copagoPlanificado) {
  // metodoPlanificado/copagoPlanificado vienen de lo cargado al reservar el turno (sección 4.4)
  // — se usan como valor por defecto del formulario de cobro en vez de perderse.
  document.getElementById('cobro-id-factura').value = idFactura;
  document.getElementById('cobro-origen').value    = 'facturacion';
  document.getElementById('cobro-importe').value   = importeTotal || '';
  document.getElementById('cobro-copago').value    = copagoPlanificado || '';
  document.getElementById('cobro-metodo').value    = metodoPlanificado || 'EFECTIVO';
  recalcularCobroOs();
  openModal('modal-cobro');
}

// "Cubierto OS" ya no se carga a mano: se calcula solo como Total - Copago (RN-025) cada vez
// que se edita alguno de los dos. Es lo que la obra social le paga al médico directo, no entra
// a la caja del consultorio — informativo, no se cobra por este sistema.
function recalcularCobroOs() {
  const total = parseFloat(document.getElementById('cobro-importe').value) || 0;
  const copago = parseFloat(document.getElementById('cobro-copago').value) || 0;
  document.getElementById('cobro-os').value = Math.max(0, total - copago).toFixed(2);
}

async function registrarCobro() {
  const idFactura = document.getElementById('cobro-id-factura').value;
  const origen = document.getElementById('cobro-origen').value || 'facturacion';
  const payload = {
    metodoPago:    document.getElementById('cobro-metodo').value,
    importeTotal:  parseFloat(document.getElementById('cobro-importe').value) || 0,
    importeCopago: parseFloat(document.getElementById('cobro-copago').value) || 0,
  };
  if (!payload.importeTotal) { Toast.error('Ingresá el importe total.'); return; }
  try {
    await Api.registrarCobro(idFactura, payload);
    Toast.success('Cobro registrado correctamente.');
    closeModal('modal-cobro');
    // El cobro se puede disparar desde el tablero de Turnos (paciente en sala de espera) o
    // desde el módulo Facturación — refrescamos la vista de donde vino.
    if (origen === 'turnos' && typeof loadTurnos === 'function') loadTurnos();
    else loadFacturacion();
  } catch (e) {
    Toast.error(e.message);
  }
}
