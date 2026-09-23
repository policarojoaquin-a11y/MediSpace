// ==================== LIQUIDACIONES MODULE ====================
let liquidacionesData = [];
let liquidacionesMedicosCache = [];

const THEAD_LIQUIDACIONES_ADMIN =
  '<tr><th>Médico</th><th>Desde</th><th>Hasta</th><th>Total Facturado</th><th>Total Médico</th><th>Total Consultorio</th><th>Estado</th><th>Acciones</th></tr>';
const THEAD_LIQUIDACIONES_MEDICO =
  '<tr><th>Desde</th><th>Hasta</th><th>Total Facturado</th><th>Total Médico</th><th>Total Consultorio</th><th>Estado</th></tr>';

async function loadLiquidaciones() {
  const rol = getRol();
  const esAdmin = rol !== 'MEDICO';
  document.getElementById('thead-liquidaciones').innerHTML = esAdmin ? THEAD_LIQUIDACIONES_ADMIN : THEAD_LIQUIDACIONES_MEDICO;
  document.getElementById('liquidaciones-filtros')?.classList.toggle('hidden', !esAdmin);
  document.getElementById('btn-generar-liquidacion')?.classList.toggle('hidden', !esAdmin);

  const tbody = document.getElementById('tabla-liquidaciones');
  const colspan = esAdmin ? 8 : 6;
  tbody.innerHTML = `<tr><td colspan="${colspan}"><div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div></td></tr>`;

  try {
    if (esAdmin) {
      await populateLiquidacionMedicoFiltro();
      const params = {
        idMedico: document.getElementById('filtro-liq-medico')?.value || '',
        desde: document.getElementById('filtro-liq-desde')?.value || '',
        hasta: document.getElementById('filtro-liq-hasta')?.value || '',
      };
      liquidacionesData = await Api.buscarLiquidaciones(params);
    } else {
      liquidacionesData = await Api.getMisLiquidaciones();
    }
    renderLiquidaciones(liquidacionesData, esAdmin);
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="${colspan}"><div class="alert alert-error">${e.message}</div></td></tr>`;
  }
}

function renderLiquidaciones(data, esAdmin) {
  const tbody = document.getElementById('tabla-liquidaciones');
  const colspan = esAdmin ? 8 : 6;
  if (!data || data.length === 0) {
    tbody.innerHTML = `<tr><td colspan="${colspan}"><div class="empty-state"><svg width="40" height="40" viewBox="0 0 24 24" fill="none"><line x1="12" y1="1" x2="12" y2="23" stroke="currentColor" stroke-width="2"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" stroke="currentColor" stroke-width="2"/></svg><h3>Sin liquidaciones</h3><p>${esAdmin ? 'Generá la primera liquidación.' : 'Todavía no tenés liquidaciones generadas.'}</p></div></td></tr>`;
    return;
  }
  const fmt = (n) => '$' + Number(n || 0).toLocaleString('es-AR', { minimumFractionDigits: 2 });
  tbody.innerHTML = data.map(l => `
    <tr>
      ${esAdmin ? `<td><strong>${l.nombreMedico || '—'}</strong></td>` : ''}
      <td>${l.fechaDesde || '—'}</td>
      <td>${l.fechaHasta || '—'}</td>
      <td>${fmt(l.totalFacturado)}</td>
      <td>${fmt(l.totalMedico)}</td>
      <td>${fmt(l.totalConsultorio)}</td>
      <td><span class="badge ${l.estado === 'ANULADA' ? 'badge-inactivo' : 'badge-activo'}">${l.estado}</span></td>
      ${esAdmin ? `<td>
        ${l.estado !== 'ANULADA' && getRol() === 'GERENTE'
          ? `<button class="btn-icon" title="Anular" onclick="anularLiquidacionAccion(${l.idLiquidacion})" style="color:var(--rojo)">
               <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><polyline points="3 6 5 6 21 6" stroke="currentColor" stroke-width="2"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" stroke="currentColor" stroke-width="2"/></svg>
             </button>`
          : '—'}
      </td>` : ''}
    </tr>`).join('');
}

async function populateLiquidacionMedicoFiltro() {
  const sel = document.getElementById('filtro-liq-medico');
  if (!sel) return;
  if (!liquidacionesMedicosCache.length) {
    liquidacionesMedicosCache = await Api.getMedicos().catch(() => []);
  }
  const seleccionado = sel.value;
  sel.innerHTML = '<option value="">Todos los médicos</option>' +
    liquidacionesMedicosCache.map(m => `<option value="${m.idMedico}">Dr/a. ${m.apellido}, ${m.nombre}</option>`).join('');
  sel.value = seleccionado || '';
}

document.getElementById('filtro-liq-medico')?.addEventListener('change', loadLiquidaciones);
document.getElementById('filtro-liq-desde')?.addEventListener('change', loadLiquidaciones);
document.getElementById('filtro-liq-hasta')?.addEventListener('change', loadLiquidaciones);

document.getElementById('btn-generar-liquidacion')?.addEventListener('click', async () => {
  document.getElementById('liq-gen-medico').value = '';
  document.getElementById('liq-gen-desde').value = '';
  document.getElementById('liq-gen-hasta').value = '';
  const sel = document.getElementById('liq-gen-medico');
  if (!liquidacionesMedicosCache.length) {
    liquidacionesMedicosCache = await Api.getMedicos().catch(() => []);
  }
  sel.innerHTML = '<option value="">Seleccioná un médico…</option>' +
    liquidacionesMedicosCache.map(m => `<option value="${m.idMedico}">Dr/a. ${m.apellido}, ${m.nombre}</option>`).join('');
  openModal('modal-generar-liquidacion');
});

async function confirmarGenerarLiquidacion() {
  const btn = document.getElementById('btn-confirmar-generar-liquidacion');
  const payload = {
    idMedico: parseInt(document.getElementById('liq-gen-medico').value) || null,
    fechaDesde: document.getElementById('liq-gen-desde').value,
    fechaHasta: document.getElementById('liq-gen-hasta').value,
  };
  if (!payload.idMedico || !payload.fechaDesde || !payload.fechaHasta) {
    Toast.error('Completá médico, fecha desde y fecha hasta.');
    return;
  }
  btn.disabled = true;
  try {
    await Api.generarLiquidacion(payload);
    Toast.success('Liquidación generada.');
    closeModal('modal-generar-liquidacion');
    loadLiquidaciones();
  } catch (e) {
    Toast.error(e.message);
  } finally {
    btn.disabled = false;
  }
}

async function anularLiquidacionAccion(id) {
  const motivo = prompt('Ingresá el motivo de la anulación:');
  if (!motivo?.trim()) return;
  try {
    await Api.anularLiquidacion(id, motivo);
    Toast.success('Liquidación anulada.');
    loadLiquidaciones();
  } catch (e) {
    Toast.error(e.message);
  }
}
