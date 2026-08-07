// ==================== DASHBOARD MODULE ====================
async function loadDashboard() {
  document.getElementById('dashboard-date-label').textContent =
    'Indicadores al ' + new Date().toLocaleDateString('es-AR', { weekday:'long', day:'numeric', month:'long' });

  // Set stats to loading state
  ['stat-total-turnos','stat-atendidos','stat-cobros-pendientes','stat-facturacion','stat-nuevos-pacientes','stat-cancelados'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.innerHTML = '<span class="spinner" style="width:18px;height:18px;border-width:2px;"></span>';
  });

  try {
    const data = await Api.getDashboard();
    setStatValue('stat-total-turnos', data.totalTurnosDia ?? 0);
    setStatValue('stat-atendidos', data.turnosAtendidos ?? 0);
    setStatValue('stat-cobros-pendientes', '$' + formatCurrency(data.cobrosPendientes ?? 0));
    setStatValue('stat-facturacion', '$' + formatCurrency(data.facturacionTotalDia ?? 0));
    setStatValue('stat-nuevos-pacientes', data.nuevosPacientes ?? 0);
    setStatValue('stat-cancelados', (data.turnosCancelados ?? 0) + (data.turnosNoAsistio ?? 0));
  } catch (e) {
    ['stat-total-turnos','stat-atendidos','stat-cobros-pendientes','stat-facturacion','stat-nuevos-pacientes','stat-cancelados'].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.textContent = '—';
    });
    Toast.warning('No se pudo cargar el dashboard: ' + e.message);
  }
}

function setStatValue(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val;
}

function formatCurrency(val) {
  return Number(val).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// ==================== REPORTES MODULE ====================
function setDefaultReporteDates() {
  const hoy = new Date();
  const desde = new Date(hoy.getFullYear(), hoy.getMonth(), 1);
  document.getElementById('reporte-desde').value = desde.toISOString().split('T')[0];
  document.getElementById('reporte-hasta').value = hoy.toISOString().split('T')[0];
}
setDefaultReporteDates();

async function loadReporteFacturacion() {
  const desde = document.getElementById('reporte-desde').value;
  const hasta = document.getElementById('reporte-hasta').value;
  if (!desde || !hasta) { Toast.error('Seleccioná el período.'); return; }

  const container = document.getElementById('reporte-resultado');
  container.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Generando reporte…</span></div>`;
  try {
    const data = await Api.reporteFacturacionMedico(desde, hasta);
    container.innerHTML = renderTablaReporte(data, 'Facturación por Médico', [
      { key: 'nombreMedico',    label: 'Médico' },
      { key: 'cantidadTurnos',  label: 'Turnos' },
      { key: 'totalFacturado',  label: 'Total Facturado',  currency: true },
      { key: 'totalCobrado',    label: 'Cobrado',          currency: true },
      { key: 'totalPendiente',  label: 'Pendiente',        currency: true },
      { key: 'totalCopago',     label: 'Copago',           currency: true },
    ]);
  } catch (e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

async function loadReporteOS() {
  const desde = document.getElementById('reporte-desde').value;
  const hasta = document.getElementById('reporte-hasta').value;
  if (!desde || !hasta) { Toast.error('Seleccioná el período.'); return; }

  const container = document.getElementById('reporte-resultado');
  container.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Generando reporte…</span></div>`;
  try {
    const data = await Api.reporteFacturacionOS(desde, hasta);
    container.innerHTML = renderTablaReporte(data, 'Facturación por Obra Social', [
      { key: 'obraSocial',      label: 'Obra Social' },
      { key: 'cantidadTurnos',  label: 'Atenciones' },
      { key: 'totalFacturado',  label: 'Total',  currency: true },
      { key: 'totalCopago',     label: 'Copago', currency: true },
    ]);
  } catch (e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

async function loadReporteConsultorios() {
  const desde = document.getElementById('reporte-desde').value;
  const hasta = document.getElementById('reporte-hasta').value;
  if (!desde || !hasta) { Toast.error('Seleccioná el período.'); return; }

  const container = document.getElementById('reporte-resultado');
  container.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Generando reporte…</span></div>`;
  try {
    const data = await Api.reporteConsultorios(desde, hasta);
    container.innerHTML = renderTablaReporte(data, 'Uso de Consultorios', [
      { key: 'numeroConsultorio',   label: 'Consultorio' },
      { key: 'nombreMedico',        label: 'Médico' },
      { key: 'totalSesiones',       label: 'Sesiones' },
      { key: 'totalPacientesAtendidos', label: 'Pacientes' },
      { key: 'facturacionGenerada', label: 'Facturación', currency: true },
      { key: 'importeConsultorio',  label: 'Consultorio (30%)', currency: true },
      { key: 'importeMedico',       label: 'Médico (70%)', currency: true },
    ]);
  } catch (e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

function renderTablaReporte(data, title, cols) {
  if (!data || data.length === 0) {
    return `<div class="empty-state"><h3>Sin datos para el período seleccionado.</h3></div>`;
  }
  const headers = cols.map(c => `<th>${c.label}</th>`).join('');
  const rows = data.map(row =>
    `<tr>${cols.map(c => {
      const val = row[c.key];
      return `<td>${c.currency ? '$' + Number(val || 0).toLocaleString('es-AR', {minimumFractionDigits:2}) : (val ?? '—')}</td>`;
    }).join('')}</tr>`
  ).join('');

  return `
    <div class="card">
      <div class="card-header"><span class="card-title">${title}</span></div>
      <div class="table-wrapper" style="border:none;">
        <table><thead><tr>${headers}</tr></thead><tbody>${rows}</tbody></table>
      </div>
    </div>`;
}
