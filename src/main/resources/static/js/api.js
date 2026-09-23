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
      let msg = data?.message || data?.error || `Error ${response.status}`;
      // El backend agrega `detail` (tipo de excepción) en los 500 — útil para diagnosticar.
      if (data?.detail && data.detail !== msg) msg += ` [${data.detail}]`;
      throw new Error(msg);
    }

    return data;
  }

  // Subida multipart — a diferencia de request(), no fija Content-Type (el browser arma el
  // boundary de multipart/form-data solo) y no serializa el body a JSON.
  async function uploadFile(path, formData) {
    const response = await fetch(`${API_BASE}${path}`, {
      method: 'POST',
      headers: getHeaders(false),
      body: formData,
    });

    if (response.status === 401) {
      removeToken();
      window.location.href = '/';
      return;
    }

    const data = await response.json().catch(() => null);
    if (!response.ok) {
      const msg = data?.message || data?.error || `Error ${response.status}`;
      throw new Error(msg);
    }
    return data;
  }

  // Descarga autenticada — devuelve un Blob (no se puede usar un <a href> plano porque el
  // JWT va en el header Authorization, no como cookie).
  async function downloadFile(path) {
    const response = await fetch(`${API_BASE}${path}`, { headers: getHeaders(false) });

    if (response.status === 401) {
      removeToken();
      window.location.href = '/';
      return;
    }
    if (!response.ok) {
      const data = await response.json().catch(() => null);
      throw new Error(data?.message || `Error ${response.status}`);
    }
    return response.blob();
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
    getMedicos: (params = {}) => {
      const qs = new URLSearchParams();
      Object.entries(params).forEach(([key, value]) => {
        if (value === null || value === undefined || value === '') return;
        qs.append(key, value);
      });
      const s = qs.toString();
      return request('GET', `/medicos${s ? '?' + s : ''}`);
    },
    getMedico: (id) => request('GET', `/medicos/${id}`),
    createMedico: (data) => request('POST', '/medicos', data),
    updateMedico: (id, data) => request('PUT', `/medicos/${id}`, data),
    deleteMedico: (id) => request('DELETE', `/medicos/${id}`),
    reactivarMedico: (id) => request('PUT', `/medicos/${id}/reactivar`),

    // ---- Usuarios ----
    getUsuarios: (params = {}) => {
      const qs = new URLSearchParams();
      Object.entries(params).forEach(([key, value]) => {
        if (value === null || value === undefined || value === '') return;
        qs.append(key, value);
      });
      const s = qs.toString();
      return request('GET', `/usuarios${s ? '?' + s : ''}`);
    },
    getUsuario: (id) => request('GET', `/usuarios/${id}`),
    createUsuario: (data) => request('POST', '/usuarios', data),
    updateUsuario: (id, data) => request('PUT', `/usuarios/${id}`, data),
    deleteUsuario: (id) => request('DELETE', `/usuarios/${id}`),
    reactivarUsuario: (id) => request('PUT', `/usuarios/${id}/reactivar`),

    // ---- Consultorios ----
    getConsultorios: () => request('GET', '/consultorios'),
    createConsultorio: (data) => request('POST', '/consultorios', data),
    updateConsultorioEstado: (id, estado) => request('PUT', `/consultorios/${id}/estado`, { estado }),

    // ---- Especialidades ----
    getEspecialidades: () => request('GET', '/especialidades'),
    createEspecialidad: (nombre) => request('POST', '/especialidades', { nombre }),

    // ---- Obras Sociales ----
    getObrasSociales: (params = {}) => {
      const qs = new URLSearchParams();
      Object.entries(params).forEach(([key, value]) => {
        if (value === null || value === undefined || value === '') return;
        qs.append(key, value);
      });
      const s = qs.toString();
      return request('GET', `/obras-sociales${s ? '?' + s : ''}`);
    },
    createObraSocial: (data) => request('POST', '/obras-sociales', data),
    updateObraSocial: (id, data) => request('PUT', `/obras-sociales/${id}`, data),
    deleteObraSocial: (id) => request('DELETE', `/obras-sociales/${id}`),
    reactivarObraSocial: (id) => request('PUT', `/obras-sociales/${id}/reactivar`),

    // ---- Mis Datos ----
    getMisDatosMedico: () => request('GET', '/medicos/me'),
    updateMisDatos: (data) => request('PUT', '/medicos/me', data),
    getMisPrestaciones: () => request('GET', '/medicos/me/prestaciones'),
    addMiPrestacion: (data) => request('POST', '/medicos/me/prestaciones', data),
    updateMiPrestacion: (idMedicoPrestacion, data) => request('PUT', `/medicos/me/prestaciones/${idMedicoPrestacion}`, data),
    deleteMiPrestacion: (idMedicoPrestacion) => request('DELETE', `/medicos/me/prestaciones/${idMedicoPrestacion}`),
    addMiObraSocial: (data) => request('POST', '/medicos/me/obras-sociales', data),
    deleteMiObraSocial: (idMedicoObraSocial) => request('DELETE', `/medicos/me/obras-sociales/${idMedicoObraSocial}`),

    // ---- Prestaciones de un médico ----
    getMedicoPrestaciones: (idMedico) => request('GET', `/medicos/${idMedico}/prestaciones`),
    addMedicoPrestacion: (idMedico, data) => request('POST', `/medicos/${idMedico}/prestaciones`, data),
    updateMedicoPrestacion: (idMedicoPrestacion, data) => request('PUT', `/medicos/prestaciones/${idMedicoPrestacion}`, data),
    deleteMedicoPrestacion: (idMedicoPrestacion) => request('DELETE', `/medicos/prestaciones/${idMedicoPrestacion}`),

    // ---- Obras Sociales de un médico (con coseguro) ----
    getMedicoObrasSociales: (idMedico) => request('GET', `/medicos/${idMedico}/obras-sociales`),
    addMedicoObraSocial: (idMedico, data) => request('POST', `/medicos/${idMedico}/obras-sociales`, data),
    updateMedicoObraSocial: (idMedicoObraSocial, data) => request('PUT', `/medicos/obras-sociales/${idMedicoObraSocial}`, data),
    deleteMedicoObraSocial: (idMedicoObraSocial) => request('DELETE', `/medicos/obras-sociales/${idMedicoObraSocial}`),
    getMisObrasSociales: () => request('GET', '/medicos/me/obras-sociales'),
    updateMiCoseguro: (idMedicoObraSocial, data) => request('PUT', `/medicos/me/obras-sociales/${idMedicoObraSocial}`, data),

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
    cancelarDiaMedico: (data) => request('POST', '/turnos/cancelar-dia', data),

    // ---- Historias Clínicas ----
    getHistoriaClinica: (idPaciente) => request('GET', `/historias-clinicas/paciente/${idPaciente}`),
    addEvolucion: (idHC, data) => request('POST', `/historias-clinicas/${idHC}/evoluciones`, data),
    editEvolucion: (idEvol, data) => request('PUT', `/historias-clinicas/evoluciones/${idEvol}`, data),
    anularEvolucion: (idEvol, motivoAnulacion) =>
      request('DELETE', `/historias-clinicas/evoluciones/${idEvol}/anular`, { motivoAnulacion }),
    addAdjunto: (idEvolucion, file) => {
      const formData = new FormData();
      formData.append('file', file);
      return uploadFile(`/historias-clinicas/evoluciones/${idEvolucion}/adjuntos`, formData);
    },
    descargarAdjunto: (idAdjunto) => downloadFile(`/historias-clinicas/adjuntos/${idAdjunto}/descargar`),

    // ---- Facturación ----
    getFacturaciones: (params = {}) => {
      const qs = new URLSearchParams();
      Object.entries(params).forEach(([k, v]) => { if (v) qs.append(k, v); });
      const s = qs.toString();
      return request('GET', `/facturacion${s ? '?' + s : ''}`);
    },
    getFacturacion: (id) => request('GET', `/facturacion/${id}`),
    registrarCobro: (id, data) => request('POST', `/facturacion/${id}/cobro`, data),
    confirmarTransferencia: (id) => request('PUT', `/facturacion/${id}/confirmar-transferencia`),

    // ---- Liquidaciones ----
    generarLiquidacion: (data) => request('POST', '/liquidaciones/generar', data),
    anularLiquidacion: (id, motivo) => request('PUT', `/liquidaciones/${id}/anular`, { motivo }),
    getLiquidaciones: (idMedico) => request('GET', `/liquidaciones/medico/${idMedico}`),
    getMisLiquidaciones: () => request('GET', '/liquidaciones/me'),
    buscarLiquidaciones: (params = {}) => {
      const qs = new URLSearchParams();
      Object.entries(params).forEach(([key, value]) => {
        if (value === null || value === undefined || value === '') return;
        qs.append(key, value);
      });
      const s = qs.toString();
      return request('GET', `/liquidaciones${s ? '?' + s : ''}`);
    },

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
