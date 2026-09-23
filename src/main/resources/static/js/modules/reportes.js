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
let ultimoReporte = null; // { data, title, cols } — para exportar a CSV

// Marca visualmente cuál de los 3 reportes está activo (antes "Facturación por Médico"
// quedaba siempre en azul como si estuviera apretado — era btn-primary fijo en el HTML).
function marcarReporteActivo(key) {
  document.querySelectorAll('#reporte-botones button').forEach(b => {
    b.classList.toggle('active', b.dataset.reporte === key);
  });
}

function setDefaultReporteDates() {
  const hoy = new Date();
  const desde = new Date(hoy.getFullYear(), hoy.getMonth(), 1);
  document.getElementById('reporte-desde').value = desde.toISOString().split('T')[0];
  document.getElementById('reporte-hasta').value = hoy.toISOString().split('T')[0];
}
setDefaultReporteDates();

function periodoReporte() {
  const desde = document.getElementById('reporte-desde').value;
  const hasta = document.getElementById('reporte-hasta').value;
  if (!desde || !hasta) { Toast.error('Seleccioná el período.'); return null; }
  return { desde, hasta };
}

async function loadReporteFacturacion() {
  const p = periodoReporte(); if (!p) return;
  marcarReporteActivo('facturacion');
  const container = document.getElementById('reporte-resultado');
  container.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Generando reporte…</span></div>`;
  try {
    const data = await Api.reporteFacturacionMedico(p.desde, p.hasta);
    container.innerHTML = renderTablaReporte(data, 'Facturación por Médico', [
      { key: 'nombreMedico',    label: 'Médico' },
      { key: 'cantidadTurnos',  label: 'Turnos' },
      { key: 'totalFacturado',  label: 'Total Facturado',  currency: true },
      { key: 'totalCobrado',    label: 'Cobrado',          currency: true },
      { key: 'totalPendiente',  label: 'Pendiente',        currency: true },
      { key: 'totalCubiertoOs', label: 'Cubierto OS',      currency: true },
    ]);
  } catch (e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

async function loadReporteOS() {
  const p = periodoReporte(); if (!p) return;
  marcarReporteActivo('os');
  const container = document.getElementById('reporte-resultado');
  container.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Generando reporte…</span></div>`;
  try {
    const data = await Api.reporteFacturacionOS(p.desde, p.hasta);
    container.innerHTML = renderTablaReporte(data, 'Facturación por Obra Social', [
      { key: 'obraSocial',      label: 'Obra Social' },
      { key: 'cantidadTurnos',  label: 'Atenciones' },
      { key: 'totalFacturado',  label: 'Total',  currency: true },
      { key: 'totalCubiertoOs', label: 'Cubierto OS', currency: true },
    ]);
  } catch (e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

async function loadReporteConsultorios() {
  const p = periodoReporte(); if (!p) return;
  marcarReporteActivo('consultorios');
  const container = document.getElementById('reporte-resultado');
  container.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Generando reporte…</span></div>`;
  try {
    const data = await Api.reporteConsultorios(p.desde, p.hasta);
    container.innerHTML = renderTablaReporte(data, 'Uso de Consultorios', [
      { key: 'numeroConsultorio',   label: 'Consultorio' },
      { key: 'nombreMedico',        label: 'Médico' },
      { key: 'totalSesiones',       label: 'Sesiones' },
      { key: 'totalPacientesAtendidos', label: 'Pacientes' },
      { key: 'facturacionGenerada', label: 'Facturación', currency: true },
      { key: 'importeConsultorio',  label: 'Parte Consultorio', currency: true },
      { key: 'importeMedico',       label: 'Parte Médico', currency: true },
    ]);
  } catch (e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

function renderTablaReporte(data, title, cols) {
  if (!data || data.length === 0) {
    ultimoReporte = null;
    return `<div class="empty-state"><h3>Sin datos para el período seleccionado.</h3></div>`;
  }
  ultimoReporte = { data, title, cols };
  const headers = cols.map(c => `<th>${c.label}</th>`).join('');
  const rows = data.map(row =>
    `<tr>${cols.map(c => {
      const val = row[c.key];
      return `<td>${c.currency ? '$' + Number(val || 0).toLocaleString('es-AR', {minimumFractionDigits:2}) : (val ?? '—')}</td>`;
    }).join('')}</tr>`
  ).join('');

  return `
    <div class="card">
      <div class="card-header">
        <span class="card-title">${title}</span>
        <button class="btn btn-secondary btn-sm" onclick="descargarReporteCSV()">Descargar CSV</button>
      </div>
      <div class="table-wrapper" style="border:none;">
        <table><thead><tr>${headers}</tr></thead><tbody>${rows}</tbody></table>
      </div>
    </div>`;
}

// Exporta a CSV el reporte que está en pantalla (pedido nuevo, checklist 27/08). Se arma en
// el cliente a partir de los datos ya cargados — no hay endpoint de exportación en el backend.
function descargarReporteCSV() {
  if (!ultimoReporte) { Toast.error('Generá un reporte primero.'); return; }
  const { data, title, cols } = ultimoReporte;
  const escapar = (v) => {
    const s = (v === null || v === undefined) ? '' : String(v);
    return /[";\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const encabezado = cols.map(c => escapar(c.label)).join(';');
  const filas = data.map(row => cols.map(c => {
    const val = row[c.key];
    if (c.currency) return escapar(Number(val || 0).toFixed(2));
    return escapar(val ?? '');
  }).join(';'));
  const desde = document.getElementById('reporte-desde').value;
  const hasta = document.getElementById('reporte-hasta').value;
  // BOM UTF-8 para que Excel abra las tildes correctamente.
  const bom = String.fromCharCode(0xFEFF);
  const csv = bom + [encabezado, ...filas].join('\r\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${title.replace(/\s+/g, '_')}_${desde}_a_${hasta}.csv`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
