// ==================== ARRENDAMIENTO / CONTRATOS MODULE ====================
let arrendamientosData = [];
let contratosFiltroMedico = '';
let contratosFiltroEstado = 'ACTIVO';

const DIA_LABEL = {
  LUNES: 'Lunes', MARTES: 'Martes', MIERCOLES: 'Miércoles',
  JUEVES: 'Jueves', VIERNES: 'Viernes', SABADO: 'Sábado', DOMINGO: 'Domingo',
};

async function fetchArrendamientosData() {
  arrendamientosData = await Api.getContratos();
  return arrendamientosData;
}

// ---- Arrendamiento: consultorios y quién los ocupa ----
async function loadArrendamientos() {
  const btnNuevo = document.getElementById('btn-nuevo-consultorio');
  if (btnNuevo) btnNuevo.classList.toggle('hidden', getRol() !== 'GERENTE');

  const grid = document.getElementById('consultorios-grid');
  grid.innerHTML = `<div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div>`;
  try {
    const [consultorios] = await Promise.all([
      Api.getConsultorios(),
      fetchArrendamientosData(),
    ]);
    renderConsultoriosGrid(consultorios, arrendamientosData);
  } catch (e) {
    grid.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

function renderConsultoriosGrid(consultorios, arrendamientos) {
  const grid = document.getElementById('consultorios-grid');
  if (!consultorios || consultorios.length === 0) {
    grid.innerHTML = `<div class="empty-state"><svg width="40" height="40" viewBox="0 0 24 24" fill="none"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" stroke="currentColor" stroke-width="2"/></svg><h3>Sin consultorios</h3><p>Registrá el primer consultorio.</p></div>`;
    return;
  }
  grid.innerHTML = consultorios.map(c => {
    const ocupantes = (arrendamientos || []).filter(a => a.idConsultorio === c.idConsultorio && a.estado === 'ACTIVO');
    const ocupado = ocupantes.length > 0;
    const ocupantesHtml = ocupado
      ? ocupantes.map(a => `
          <div class="consultorio-ocupante">
            <span>Dr/a. ${a.nombreMedico || 'Médico #' + a.idMedico}</span>
            <span class="consultorio-ocupante-horario">${DIA_LABEL[a.diaSemana] || a.diaSemana} ${a.horaInicio || '—'}-${a.horaFin || '—'}</span>
          </div>`).join('')
      : `<div class="text-muted text-sm">Sin ocupantes</div>`;

    return `
      <div class="consultorio-card ${ocupado ? 'ocupado' : 'disponible'}">
        <div class="consultorio-card-header">
          <span class="consultorio-numero">Consultorio ${c.numeroConsultorio}</span>
          <span class="badge ${ocupado ? 'badge-en-espera' : 'badge-disponible'}">${ocupado ? 'OCUPADO' : 'DISPONIBLE'}</span>
        </div>
        <div class="consultorio-card-body">
          ${c.ubicacion ? `<div class="consultorio-meta">${c.ubicacion}</div>` : ''}
          ${c.equipamiento ? `<div class="consultorio-meta">${c.equipamiento}</div>` : ''}
          <div class="consultorio-ocupantes">${ocupantesHtml}</div>
        </div>
      </div>`;
  }).join('');
}

document.getElementById('btn-nuevo-consultorio')?.addEventListener('click', () => {
  document.getElementById('cons-numero').value = '';
  document.getElementById('cons-ubicacion').value = '';
  document.getElementById('cons-equipamiento').value = '';
  document.getElementById('cons-descripcion').value = '';
  openModal('modal-consultorio');
});

async function confirmarNuevoConsultorio() {
  const numeroConsultorio = document.getElementById('cons-numero').value.trim();
  if (!numeroConsultorio) {
    Toast.error('Ingresá el número de consultorio.');
    return;
  }
  const payload = {
    numeroConsultorio,
    ubicacion: document.getElementById('cons-ubicacion').value.trim(),
    equipamiento: document.getElementById('cons-equipamiento').value.trim(),
    descripcion: document.getElementById('cons-descripcion').value.trim(),
  };
  try {
    await Api.createConsultorio(payload);
    Toast.success('Consultorio creado correctamente.');
    closeModal('modal-consultorio');
    loadArrendamientos();
  } catch (e) {
    Toast.error(e.message);
  }
}

// ---- Contratos: detalle de arrendamientos ----
async function loadContratos() {
  // Solo GERENTE puede crear contratos nuevos (RN de negocio)
  const btnNuevo = document.getElementById('btn-nuevo-arrendamiento');
  if (btnNuevo) btnNuevo.classList.toggle('hidden', getRol() !== 'GERENTE');

  const tbody = document.getElementById('tabla-contratos');
  tbody.innerHTML = `<tr><td colspan="8"><div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div></td></tr>`;
  try {
    if (!medicosData || !medicosData.length) {
      await loadMedicos().catch(() => {});
    }
    await populateFiltroContratosMedico();
    await fetchArrendamientosData();
    aplicarFiltrosContratos();
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="8"><div class="alert alert-error">${e.message}</div></td></tr>`;
  }
}

async function populateFiltroContratosMedico() {
  const sel = document.getElementById('filtro-contratos-medico');
  if (!sel) return;
  sel.innerHTML = '<option value="">Todos los médicos</option>';
  (medicosData || []).forEach(m => {
    const opt = document.createElement('option');
    opt.value = m.idMedico;
    opt.textContent = `Dr/a. ${m.apellido}, ${m.nombre}`;
    sel.appendChild(opt);
  });
  sel.value = contratosFiltroMedico;
}

function aplicarFiltrosContratos() {
  let data = arrendamientosData;
  if (contratosFiltroMedico) {
    data = data.filter(a => String(a.idMedico) === String(contratosFiltroMedico));
  }
  if (contratosFiltroEstado !== 'TODOS') {
    data = data.filter(a => a.estado === contratosFiltroEstado);
  }
  renderContratos(data);
}

document.getElementById('filtro-contratos-medico')?.addEventListener('change', (e) => {
  contratosFiltroMedico = e.target.value;
  aplicarFiltrosContratos();
});

document.getElementById('filtro-contratos-estado')?.addEventListener('click', (e) => {
  const btn = e.target.closest('button[data-estado]');
  if (!btn) return;
  contratosFiltroEstado = btn.dataset.estado;
  document.querySelectorAll('#filtro-contratos-estado button').forEach(b => b.classList.toggle('active', b === btn));
  aplicarFiltrosContratos();
});

function renderContratos(data) {
  const tbody = document.getElementById('tabla-contratos');
  if (!data || data.length === 0) {
    tbody.innerHTML = `<tr><td colspan="8"><div class="empty-state"><svg width="40" height="40" viewBox="0 0 24 24" fill="none"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" stroke="currentColor" stroke-width="2"/></svg><h3>Sin contratos</h3><p>No hay contratos de arrendamiento registrados.</p></div></td></tr>`;
    return;
  }
  tbody.innerHTML = data.map(a => `
    <tr>
      <td>${a.nombreMedico || `Médico #${a.idMedico}`}</td>
      <td>Consultorio ${a.numeroConsultorio || a.idConsultorio}</td>
      <td>${a.fechaInicio} - ${a.fechaFin || 'Indefinido'}</td>
      <td>${DIA_LABEL[a.diaSemana] || a.diaSemana || '—'}</td>
      <td>${a.horaInicio || '—'} - ${a.horaFin || '—'}</td>
      <td>${a.porcentajeConsultorio ?? 30}%</td>
      <td><span class="badge ${a.estado === 'ACTIVO' ? 'badge-activo' : 'badge-inactivo'}">${a.estado}</span></td>
      <td>
        ${a.estado === 'ACTIVO' ? `<button class="btn btn-sm" style="background:var(--rojo-light);color:var(--rojo-dim);border:none;cursor:pointer;" onclick="darDeBajaArrendamiento(${a.idArrendamiento})">Dar de baja</button>` : '—'}
      </td>
    </tr>`).join('');
}

document.getElementById('btn-nuevo-arrendamiento')?.addEventListener('click', async () => {
  if (!medicosData || !medicosData.length) {
    await loadMedicos().catch(() => {});
  }
  const selMedico = document.getElementById('arr-id-medico');
  selMedico.innerHTML = '<option value="">Seleccioná un médico…</option>';
  (medicosData || []).forEach(m => {
    const opt = document.createElement('option');
    opt.value = m.idMedico;
    opt.textContent = `Dr/a. ${m.apellido}, ${m.nombre}`;
    selMedico.appendChild(opt);
  });

  const consultorios = await Api.getConsultorios().catch(() => []);
  const selConsultorio = document.getElementById('arr-id-consultorio');
  selConsultorio.innerHTML = '<option value="">Seleccioná un consultorio…</option>';
  consultorios.forEach(c => {
    const opt = document.createElement('option');
    opt.value = c.idConsultorio;
    opt.textContent = `Consultorio ${c.numeroConsultorio}`;
    selConsultorio.appendChild(opt);
  });

  document.getElementById('arr-dia-semana').value = 'LUNES';
  document.getElementById('arr-fecha-inicio').value = '';
  document.getElementById('arr-fecha-fin').value = '';
  document.getElementById('arr-hora-inicio').value = '';
  document.getElementById('arr-hora-fin').value = '';
  document.getElementById('arr-duracion-turno').value = '30';
  document.getElementById('arr-cupo-maximo').value = '8';
  document.getElementById('arr-pct-consultorio').value = '30';
  document.getElementById('arr-pct-medico').value = '70';
  document.getElementById('arr-observaciones').value = '';

  openModal('modal-arrendamiento');
});

async function confirmarNuevoArrendamiento() {
  const idMedico = document.getElementById('arr-id-medico').value;
  const idConsultorio = document.getElementById('arr-id-consultorio').value;
  const fechaInicio = document.getElementById('arr-fecha-inicio').value;
  const fechaFin = document.getElementById('arr-fecha-fin').value;
  const horaInicio = document.getElementById('arr-hora-inicio').value;
  const horaFin = document.getElementById('arr-hora-fin').value;

  if (!idMedico || !idConsultorio || !fechaInicio || !horaInicio || !horaFin) {
    Toast.error('Completá médico, consultorio, fecha de inicio y horario.');
    return;
  }

  const payload = {
    idMedico: parseInt(idMedico),
    idConsultorio: parseInt(idConsultorio),
    fechaInicio,
    fechaFin: fechaFin || null,
    diaSemana: document.getElementById('arr-dia-semana').value,
    horaInicio,
    horaFin,
    duracionTurnoMin: parseInt(document.getElementById('arr-duracion-turno').value) || 30,
    cupoMaximoDiario: parseInt(document.getElementById('arr-cupo-maximo').value) || 8,
    porcentajeConsultorio: parseFloat(document.getElementById('arr-pct-consultorio').value) || 0,
    porcentajeMedico: parseFloat(document.getElementById('arr-pct-medico').value) || 0,
    observaciones: document.getElementById('arr-observaciones').value.trim(),
  };

  try {
    const creado = await Api.createArrendamiento(payload);
    const cantidad = creado?.turnosGenerados ?? 0;
    Toast.success(`Contrato creado. Se generaron ${cantidad} turno${cantidad === 1 ? '' : 's'} disponible${cantidad === 1 ? '' : 's'} automáticamente.`);
    closeModal('modal-arrendamiento');
    loadContratos();
  } catch (e) {
    Toast.error(e.message);
  }
}

async function darDeBajaArrendamiento(id) {
  if (!confirm('¿Dar de baja este contrato de arrendamiento?')) return;
  try {
    await Api.bajaArrendamiento(id);
    Toast.success('Contrato dado de baja.');
    loadContratos();
  } catch (e) {
    Toast.error(e.message);
  }
}
