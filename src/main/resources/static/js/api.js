/* eslint-disable */
/* ===========================
   MediSpace — API Client
   Centralizes all HTTP calls
   =========================== */

const API_BASE = '/api';

const Api = (() => {
  function getToken() {
    return localStorage.getItem('ms_token');
  }

  function setToken(token) {
    localStorage.setItem('ms_token', token);
  }

  function removeToken() {
    localStorage.removeItem('ms_token');
    localStorage.removeItem('ms_user');
  }

  function getHeaders(isJson = true) {
    const headers = {};
    if (isJson) headers['Content-Type'] = 'application/json';
    const token = getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
    return headers;
  }

  async function request(method, path, body = null) {
    const opts = {
      method,
      headers: getHeaders(),
    };
    if (body !== null) opts.body = JSON.stringify(body);

    const response = await fetch(`${API_BASE}${path}`, opts);

    // Unauthorized — redirect to login (excepto en el propio login: ahí el error
    // lo debe manejar quien llama, no una recarga global de la página)
    if (response.status === 401 && path !== '/auth/login') {
      removeToken();
      window.location.href = '/';
      return;
    }

    // No content
    if (response.status === 204) return null;

    const data = await response.json().catch(() => null);

    if (!response.ok) {
      const msg = data?.message || data?.error || `Error ${response.status}`;
      throw new Error(msg);
    }

    return data;
  }

  return {
    getToken,
    setToken,
    removeToken,

    // ---- Auth ----
    login: (email, password) =>
      request('POST', '/auth/login', { email, password }),

    // ---- Pacientes ----
    getPacientes: () => request('GET', '/pacientes'),
    getPaciente: (id) => request('GET', `/pacientes/${id}`),
    createPaciente: (data) => request('POST', '/pacientes', data),
    updatePaciente: (id, data) => request('PUT', `/pacientes/${id}`, data),
    deletePaciente: (id) => request('DELETE', `/pacientes/${id}`),

    // ---- Médicos ----
    getMedicos: () => request('GET', '/medicos'),
    getMedico: (id) => request('GET', `/medicos/${id}`),
    createMedico: (data) => request('POST', '/medicos', data),
    updateMedico: (id, data) => request('PUT', `/medicos/${id}`, data),
    deleteMedico: (id) => request('DELETE', `/medicos/${id}`),

    // ---- Consultorios ----
    getConsultorios: () => request('GET', '/consultorios'),
    createConsultorio: (data) => request('POST', '/consultorios', data),

    // ---- Especialidades ----
    getEspecialidades: () => request('GET', '/especialidades'),
    createEspecialidad: (nombre) => request('POST', '/especialidades', { nombre }),

    // ---- Obras Sociales ----
    getObrasSociales: () => request('GET', '/obras-sociales'),

    // ---- Mis Datos ----
    getMisDatosMedico: () => request('GET', '/medicos/me'),
    getMisPrestaciones: () => request('GET', '/medicos/me/prestaciones'),

    // ---- Prestaciones de un médico ----
    getMedicoPrestaciones: (idMedico) => request('GET', `/medicos/${idMedico}/prestaciones`),
    addMedicoPrestacion: (idMedico, data) => request('POST', `/medicos/${idMedico}/prestaciones`, data),
    updateMedicoPrestacion: (idMedicoPrestacion, data) => request('PUT', `/medicos/prestaciones/${idMedicoPrestacion}`, data),
    deleteMedicoPrestacion: (idMedicoPrestacion) => request('DELETE', `/medicos/prestaciones/${idMedicoPrestacion}`),

    // ---- Turnos ----
    getTurnos: (params = {}) => {
      const qs = new URLSearchParams();
      Object.entries(params).forEach(([key, value]) => {
        if (value === null || value === undefined || value === '') return;
        if (Array.isArray(value)) {
          value.forEach(v => qs.append(key, v));
        } else {
          qs.append(key, value);
        }
      });
      const s = qs.toString();
      return request('GET', `/turnos${s ? '?' + s : ''}`);
    },
    getTurno: (id) => request('GET', `/turnos/${id}`),
    reservarTurno: (id, data) => request('POST', `/turnos/${id}/reservar`, data),
    cambiarEstadoTurno: (id, nuevoEstado) => request('PUT', `/turnos/${id}/estado`, { nuevoEstado }),
    deleteTurno: (id) => request('DELETE', `/turnos/${id}`),

    // ---- Historias Clínicas ----
    getHistoriaClinica: (idPaciente) => request('GET', `/historias-clinicas/paciente/${idPaciente}`),
    addEvolucion: (idHC, data) => request('POST', `/historias-clinicas/${idHC}/evoluciones`, data),
    editEvolucion: (idEvol, data) => request('PUT', `/historias-clinicas/evoluciones/${idEvol}`, data),
    anularEvolucion: (idEvol, motivoAnulacion) =>
      request('DELETE', `/historias-clinicas/evoluciones/${idEvol}/anular`, { motivoAnulacion }),

    // ---- Facturación ----
    getFacturaciones: () => request('GET', '/facturacion'),
    getFacturacion: (id) => request('GET', `/facturacion/${id}`),
    registrarCobro: (id, data) => request('POST', `/facturacion/${id}/cobro`, data),

    // ---- Liquidaciones ----
    generarLiquidacion: (data) => request('POST', '/liquidaciones/generar', data),
    anularLiquidacion: (id) => request('PUT', `/liquidaciones/${id}/anular`),
    getLiquidaciones: (idMedico) => request('GET', `/liquidaciones/medico/${idMedico}`),

    // ---- Arrendamientos ----
    getArrendamientos: (idMedico) => request('GET', `/arrendamientos/medico/${idMedico}`),
    getContratos: (medicoId) => request('GET', `/arrendamientos/contratos${medicoId ? '?medicoId=' + medicoId : ''}`),
    createArrendamiento: (data) => request('POST', '/arrendamientos', data),
    bajaArrendamiento: (id) => request('PUT', `/arrendamientos/${id}/baja`),
    registrarUso: (data) => request('POST', '/arrendamientos/uso', data),
    generarCierreDiario: (data) => request('POST', '/arrendamientos/cierre-diario', data),

    // ---- Reportes / Dashboard ----
    getDashboard: () => request('GET', '/reportes/dashboard'),
    recalcularDashboard: (fecha) => request('POST', `/reportes/dashboard/recalcular?fecha=${fecha}`),
    reporteFacturacionMedico: (desde, hasta) => request('GET', `/reportes/facturacion/medico?desde=${desde}&hasta=${hasta}`),
    reporteFacturacionOS: (desde, hasta) => request('GET', `/reportes/facturacion/obra-social?desde=${desde}&hasta=${hasta}`),
    reporteConsultorios: (desde, hasta) => request('GET', `/reportes/consultorios?desde=${desde}&hasta=${hasta}`),
  };
})();
