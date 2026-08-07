// ==================== FACTURACIÓN MODULE ====================
let facturacionData = [];

const ESTADO_PAGO_BADGE = {
  PENDIENTE:   'badge-pendiente',
  PAGADO:      'badge-pagado',
  PARCIAL:     'badge-reservado',
  ANULADO:     'badge-anulada',
  REINTEGRADO: 'badge-neutral',
};

async function loadFacturacion() {
  const tbody = document.getElementById('tabla-facturacion');
  tbody.innerHTML = `<tr><td colspan="7"><div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div></td></tr>`;
  try {
    facturacionData = await Api.getFacturaciones();
    renderFacturacion(facturacionData);
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="7"><div class="alert alert-error">${e.message}</div></td></tr>`;
  }
}

function renderFacturacion(data) {
  const tbody = document.getElementById('tabla-facturacion');
  if (!data || data.length === 0) {
    tbody.innerHTML = `<tr><td colspan="7"><div class="empty-state"><svg width="40" height="40" viewBox="0 0 24 24" fill="none"><line x1="12" y1="1" x2="12" y2="23" stroke="currentColor" stroke-width="2"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" stroke="currentColor" stroke-width="2"/></svg><h3>Sin facturaciones</h3></div></td></tr>`;
    return;
  }
  tbody.innerHTML = data.map(f => {
    const fecha = f.fechaFacturacion ? new Date(f.fechaFacturacion).toLocaleDateString('es-AR') : '—';
    const badge = `<span class="badge ${ESTADO_PAGO_BADGE[f.estadoPago] || 'badge-neutral'}">${f.estadoPago}</span>`;
    const metodoPlanificado = f.metodoPagoPlanificado || 'EFECTIVO';
    const copagoPlanificado = f.importeCopago || 0;
    const acciones = f.estadoPago === 'PENDIENTE'
      ? `<button class="btn btn-success btn-sm" onclick="abrirCobro(${f.idFacturacion}, ${f.importeTotal}, '${metodoPlanificado}', ${copagoPlanificado})">Registrar Cobro</button>`
      : '—';
    return `
      <tr>
        <td>${fecha}</td>
        <td>${f.nombrePaciente || '—'}</td>
        <td>${f.nombreMedico || '—'}</td>
        <td><strong>$${Number(f.importeTotal || 0).toLocaleString('es-AR', {minimumFractionDigits:2})}</strong></td>
        <td>$${Number(f.importeCopago || 0).toLocaleString('es-AR', {minimumFractionDigits:2})}</td>
        <td>${badge}</td>
        <td>${acciones}</td>
      </tr>`;
  }).join('');
}

// Search live
document.getElementById('search-facturacion')?.addEventListener('input', (e) => {
  const q = e.target.value.toLowerCase();
  renderFacturacion(facturacionData.filter(f =>
    `${f.nombrePaciente || ''} ${f.nombreMedico || ''}`.toLowerCase().includes(q)
  ));
});

function abrirCobro(idFactura, importeTotal, metodoPlanificado, copagoPlanificado) {
  // metodoPlanificado/copagoPlanificado vienen de lo cargado al reservar el turno (sección 4.4)
  // — se usan como valor por defecto del formulario de cobro en vez de perderse.
  document.getElementById('cobro-id-factura').value = idFactura;
  document.getElementById('cobro-importe').value   = importeTotal || '';
  document.getElementById('cobro-os').value        = '';
  document.getElementById('cobro-copago').value    = copagoPlanificado || '';
  document.getElementById('cobro-metodo').value    = metodoPlanificado || 'EFECTIVO';
  openModal('modal-cobro');
}

async function registrarCobro() {
  const idFactura = document.getElementById('cobro-id-factura').value;
  const payload = {
    metodoPago:        document.getElementById('cobro-metodo').value,
    importeTotal:       parseFloat(document.getElementById('cobro-importe').value) || 0,
    importeCubiertoOs:  parseFloat(document.getElementById('cobro-os').value) || 0,
    importeCopago:      parseFloat(document.getElementById('cobro-copago').value) || 0,
  };
  if (!payload.importeTotal) { Toast.error('Ingresá el importe total.'); return; }
  try {
    await Api.registrarCobro(idFactura, payload);
    Toast.success('Cobro registrado correctamente.');
    closeModal('modal-cobro');
    loadFacturacion();
  } catch (e) {
    Toast.error(e.message);
  }
}
