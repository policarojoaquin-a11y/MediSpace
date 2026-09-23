/* ==============================================
   MediSpace — App Core JS
   Router, shell, session, toasts, nav-by-role
   ============================================== */

// ---- Toast Notifications ----
const Toast = {
  show(type, message, duration = 3500) {
    const container = document.getElementById('toast-container');
    const icons = {
      success: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="currentColor" stroke-width="2"/><polyline points="22 4 12 14.01 9 11.01" stroke="currentColor" stroke-width="2"/></svg>',
      error:   '<svg width="18" height="18" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/><line x1="15" y1="9" x2="9" y2="15" stroke="currentColor" stroke-width="2"/><line x1="9" y1="9" x2="15" y2="15" stroke="currentColor" stroke-width="2"/></svg>',
      warning: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" stroke="currentColor" stroke-width="2"/><line x1="12" y1="9" x2="12" y2="13" stroke="currentColor" stroke-width="2"/><line x1="12" y1="17" x2="12.01" y2="17" stroke="currentColor" stroke-width="2"/></svg>',
      info:    '<svg width="18" height="18" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/><line x1="12" y1="16" x2="12" y2="12" stroke="currentColor" stroke-width="2"/><line x1="12" y1="8" x2="12.01" y2="8" stroke="currentColor" stroke-width="2"/></svg>',
    };
    const t = document.createElement('div');
    t.className = `toast toast-${type}`;
    t.innerHTML = `<span class="toast-icon-${type}">${icons[type]}</span><span>${message}</span>`;
    container.appendChild(t);
    setTimeout(() => {
      t.style.opacity = '0';
      t.style.transform = 'translateX(30px)';
      t.style.transition = '.3s ease';
      setTimeout(() => t.remove(), 300);
    }, duration);
  },
  success: (msg) => Toast.show('success', msg),
  error:   (msg) => Toast.show('error',   msg),
  warning: (msg) => Toast.show('warning', msg),
  info:    (msg) => Toast.show('info',    msg),
};

// ---- Modal helpers ----
function openModal(id)  { document.getElementById(id).classList.add('open'); }
function closeModal(id) { document.getElementById(id).classList.remove('open'); }

// Click outside modal to close
document.querySelectorAll('.modal-overlay').forEach(overlay => {
  overlay.addEventListener('click', e => {
    if (e.target === overlay) overlay.classList.remove('open');
  });
});

// ---- Date display ----
function updateTopbarDate() {
  const el = document.getElementById('topbar-date');
  if (!el) return;
  const now = new Date();
  el.textContent = now.toLocaleDateString('es-AR', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
}
updateTopbarDate();
setInterval(updateTopbarDate, 60000);

// ---- Nav menus per role ----
const NAV_CONFIG = {
  GERENTE: [
    { id: 'dashboard',      label: 'Dashboard',           icon: 'grid' },
    { section: 'Gestión' },
    { id: 'usuarios',       label: 'Gestionar Usuarios',    icon: 'user-check' },
    { id: 'medicos',        label: 'Médicos',              icon: 'user-check' },
    { id: 'pacientes',      label: 'Pacientes',            icon: 'users' },
    { id: 'obras-sociales', label: 'Obras Sociales',       icon: 'file-text' },
    { id: 'arrendamientos', label: 'Arrendamiento',        icon: 'home' },
    { id: 'contratos',      label: 'Contratos',            icon: 'file-text' },
    { section: 'Finanzas' },
    { id: 'facturacion',    label: 'Facturación',          icon: 'dollar' },
    { id: 'liquidaciones',  label: 'Liquidaciones Médicas', icon: 'dollar' },
    { id: 'reportes',       label: 'Reportes',             icon: 'bar-chart' },
  ],
  ADMINISTRATIVO: [
    { id: 'pacientes',      label: 'Pacientes',            icon: 'users' },
    { id: 'obras-sociales', label: 'Obras Sociales',       icon: 'file-text' },
    { id: 'turnos',         label: 'Turnos',               icon: 'calendar' },
    { section: 'Médicos & Espacios' },
    { id: 'medicos',        label: 'Médicos',              icon: 'user-check' },
    { id: 'arrendamientos', label: 'Arrendamiento',        icon: 'home' },
    { id: 'contratos',      label: 'Contratos',            icon: 'file-text' },
    { section: 'Finanzas' },
    { id: 'facturacion',    label: 'Facturación',          icon: 'dollar' },
    { id: 'liquidaciones',  label: 'Liquidaciones Médicas', icon: 'dollar' },
    { section: 'Administración' },
    { id: 'usuarios',       label: 'Gestionar Usuarios',    icon: 'user-check' },
  ],
  MEDICO: [
    { id: 'turnos',            label: 'Mi Agenda',            icon: 'calendar' },
    { id: 'pacientes',         label: 'Mis Pacientes',        icon: 'users' },
    { id: 'historias',         label: 'Historias Clínicas',   icon: 'file-text' },
    { id: 'liquidaciones',     label: 'Mis Liquidaciones',    icon: 'dollar' },
    { id: 'mis-prestaciones',  label: 'Mis Prestaciones',     icon: 'user-check' },
    { id: 'arrendamientos',    label: 'Disponibilidad',       icon: 'home' },
    { id: 'contratos',         label: 'Contratos',            icon: 'file-text' },
    { id: 'mis-datos',         label: 'Mis Datos',            icon: 'user' },
  ],
};

const SVG_ICONS = {
  grid:       '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><rect x="3" y="3" width="7" height="7" stroke="currentColor" stroke-width="2"/><rect x="14" y="3" width="7" height="7" stroke="currentColor" stroke-width="2"/><rect x="14" y="14" width="7" height="7" stroke="currentColor" stroke-width="2"/><rect x="3" y="14" width="7" height="7" stroke="currentColor" stroke-width="2"/></svg>',
  users:      '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" stroke-width="2"/><circle cx="9" cy="7" r="4" stroke="currentColor" stroke-width="2"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" stroke="currentColor" stroke-width="2"/></svg>',
  calendar:   '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><rect x="3" y="4" width="18" height="18" rx="2" ry="2" stroke="currentColor" stroke-width="2"/><line x1="16" y1="2" x2="16" y2="6" stroke="currentColor" stroke-width="2"/><line x1="8" y1="2" x2="8" y2="6" stroke="currentColor" stroke-width="2"/><line x1="3" y1="10" x2="21" y2="10" stroke="currentColor" stroke-width="2"/></svg>',
  'file-text':'<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" stroke-width="2"/><polyline points="14 2 14 8 20 8" stroke="currentColor" stroke-width="2"/><line x1="16" y1="13" x2="8" y2="13" stroke="currentColor" stroke-width="2"/><line x1="16" y1="17" x2="8" y2="17" stroke="currentColor" stroke-width="2"/></svg>',
  dollar:     '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><line x1="12" y1="1" x2="12" y2="23" stroke="currentColor" stroke-width="2"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" stroke="currentColor" stroke-width="2"/></svg>',
  'bar-chart':'<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><line x1="18" y1="20" x2="18" y2="10" stroke="currentColor" stroke-width="2"/><line x1="12" y1="20" x2="12" y2="4" stroke="currentColor" stroke-width="2"/><line x1="6" y1="20" x2="6" y2="14" stroke="currentColor" stroke-width="2"/></svg>',
  'user-check':'<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" stroke-width="2"/><circle cx="8.5" cy="7" r="4" stroke="currentColor" stroke-width="2"/><polyline points="17 11 19 13 23 9" stroke="currentColor" stroke-width="2"/></svg>',
  home:       '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" stroke="currentColor" stroke-width="2"/><polyline points="9 22 9 12 15 12 15 22" stroke="currentColor" stroke-width="2"/></svg>',
  user:       '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="7" r="4" stroke="currentColor" stroke-width="2"/></svg>',
};

// ---- Session ----
function getSession() {
  try { return JSON.parse(localStorage.getItem('ms_user')) || null; }
  catch { return null; }
}
function getRol() {
  const s = getSession();
  return s?.rol || s?.role || '';
}

// ---- Build Sidebar ----
function buildSidebar(rol) {
  const nav = document.getElementById('sidebar-nav');
  if (!nav) return;
  const items = NAV_CONFIG[rol] || [];
  nav.innerHTML = '';

  items.forEach(item => {
    if (item.section) {
      const lbl = document.createElement('div');
      lbl.className = 'sidebar-section-label';
      lbl.textContent = item.section;
      nav.appendChild(lbl);
      return;
    }
    const el = document.createElement('div');
    el.className = 'nav-item';
    el.dataset.view = item.id;
    el.innerHTML = `${SVG_ICONS[item.icon] || ''}<span>${item.label}</span>`;
    el.addEventListener('click', () => navigateTo(item.id, item.label));
    nav.appendChild(el);
  });
}

// ---- Navigation ----
let currentView = null;

function navigateTo(viewId, label) {
  // Hide all
  document.querySelectorAll('.module-view').forEach(v => v.classList.remove('active'));
  // Show target
  const target = document.getElementById(`view-${viewId}`);
  if (target) target.classList.add('active');
  // Highlight nav
  document.querySelectorAll('.nav-item').forEach(n => {
    n.classList.toggle('active', n.dataset.view === viewId);
  });
  // Topbar title
  const title = document.getElementById('topbar-title');
  if (title) title.textContent = label || viewId;
  // Close sidebar on mobile
  document.getElementById('sidebar').classList.remove('open');
  document.getElementById('sidebar-overlay').classList.remove('open');

  currentView = viewId;

  // Load data for the module
  switch (viewId) {
    case 'dashboard':      loadDashboard(); break;
    case 'usuarios':       loadUsuarios(); break;
    case 'pacientes':      loadPacientes(); break;
    case 'obras-sociales': loadObrasSociales(); break;
    case 'medicos':        loadMedicos();   break;
    case 'turnos':         loadTurnos();    break;
    case 'facturacion':    loadFacturacion(); break;
    case 'liquidaciones':  loadLiquidaciones(); break;
    case 'arrendamientos': loadArrendamientos(); break;
    case 'contratos':      loadContratos(); break;
    case 'mis-datos':      loadMisDatos(); break;
    case 'mis-prestaciones': loadMisPrestaciones(); break;
  }
}

// ---- Mobile sidebar toggle ----
document.getElementById('sidebar-toggle')?.addEventListener('click', () => {
  document.getElementById('sidebar').classList.toggle('open');
  document.getElementById('sidebar-overlay').classList.toggle('open');
});
document.getElementById('sidebar-overlay')?.addEventListener('click', () => {
  document.getElementById('sidebar').classList.remove('open');
  document.getElementById('sidebar-overlay').classList.remove('open');
});

// ---- Logout ----
document.getElementById('btn-logout')?.addEventListener('click', () => {
  Api.removeToken();
  window.location.href = '/';
});

// ---- Init App ----
(function init() {
  const token = Api.getToken();
  if (!token) { window.location.href = '/'; return; }

  const session = getSession();
  if (!session) { window.location.href = '/'; return; }

  const rol = session.rol || session.role || 'ADMINISTRATIVO';
  const nombre = session.nombre || session.email || 'Usuario';
  const inicial = nombre.charAt(0).toUpperCase();

  // Populate header
  document.getElementById('user-avatar').textContent = inicial;
  document.getElementById('user-display-name').textContent = nombre;
  document.getElementById('user-display-role').textContent = rol;
  document.getElementById('topbar-username').textContent = nombre;

  // Build sidebar
  buildSidebar(rol);

  // Default view by role
  const defaults = {
    GERENTE:        ['dashboard',  'Dashboard Gerencial'],
    ADMINISTRATIVO: ['pacientes',  'Pacientes'],
    MEDICO:         ['turnos',     'Mi Agenda'],
  };
  const [defaultView, defaultLabel] = defaults[rol] || defaults['ADMINISTRATIVO'];
  navigateTo(defaultView, defaultLabel);
})();
