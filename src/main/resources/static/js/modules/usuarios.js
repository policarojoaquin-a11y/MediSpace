// ==================== USUARIOS MODULE ====================
let usuariosData = [];
let editingUsuarioId = null;

const ROL_LABELS = { GERENTE: 'Gerente', ADMINISTRATIVO: 'Administrativo', MEDICO: 'Médico' };

async function loadUsuarios() {
  const tbody = document.getElementById('tabla-usuarios');
  tbody.innerHTML = `<tr><td colspan="5"><div class="loading-overlay"><div class="spinner"></div><span>Cargando…</span></div></td></tr>`;
  try {
    const params = {
      email: document.getElementById('search-usuarios')?.value.trim() || '',
      rol: document.getElementById('filtro-usuarios-rol')?.value || '',
      incluirInactivos: document.getElementById('filtro-usuarios-inactivos')?.checked || false,
    };
    usuariosData = await Api.getUsuarios(params);
    renderUsuarios(usuariosData);
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="5"><div class="alert alert-error">${e.message}</div></td></tr>`;
  }
}

function renderUsuarios(data) {
  const tbody = document.getElementById('tabla-usuarios');
  if (!data || data.length === 0) {
    tbody.innerHTML = `<tr><td colspan="5"><div class="empty-state"><svg width="40" height="40" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="2"/><path d="M4 21v-1a8 8 0 0 1 16 0v1" stroke="currentColor" stroke-width="2"/></svg><h3>Sin usuarios</h3><p>Registrá el primer usuario.</p></div></td></tr>`;
    return;
  }
  tbody.innerHTML = data.map(u => `
    <tr>
      <td><strong>${u.email}</strong></td>
      <td>${ROL_LABELS[u.rol] || u.rol}</td>
      <td>${u.fechaCreacion || '—'}</td>
      <td><span class="badge ${u.visible ? 'badge-activo' : 'badge-inactivo'}">${u.visible ? 'Activo' : 'Inactivo'}</span></td>
      <td>
        <div class="table-actions">
          <button class="btn-icon" title="Editar" onclick="editarUsuario(${u.idUsuario})">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2"/></svg>
          </button>
          ${u.visible
            ? `<button class="btn-icon" title="Dar de baja" onclick="bajaUsuario(${u.idUsuario})" style="color:var(--rojo)">
                 <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><polyline points="3 6 5 6 21 6" stroke="currentColor" stroke-width="2"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" stroke="currentColor" stroke-width="2"/></svg>
               </button>`
            : `<button class="btn-icon" title="Reactivar" onclick="reactivarUsuarioAccion(${u.idUsuario})" style="color:var(--verde)">
                 <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><polyline points="23 4 23 10 17 10" stroke="currentColor" stroke-width="2"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" stroke="currentColor" stroke-width="2"/></svg>
               </button>`}
        </div>
      </td>
    </tr>`).join('');
}

// Filtros: recargan desde el backend (el toggle "incluir inactivos" necesita ir al servidor)
document.getElementById('search-usuarios')?.addEventListener('input', debounceLoadUsuarios);
document.getElementById('filtro-usuarios-rol')?.addEventListener('change', loadUsuarios);
document.getElementById('filtro-usuarios-inactivos')?.addEventListener('change', loadUsuarios);

let usuariosDebounceTimer = null;
function debounceLoadUsuarios() {
  clearTimeout(usuariosDebounceTimer);
  usuariosDebounceTimer = setTimeout(loadUsuarios, 300);
}

document.getElementById('btn-nuevo-usuario')?.addEventListener('click', () => {
  editingUsuarioId = null;
  document.getElementById('modal-usuario-title').textContent = 'Nuevo Usuario';
  document.getElementById('usu-email').value = '';
  document.getElementById('usu-password').value = '';
  document.getElementById('usu-password-group').classList.remove('hidden');

  // Regla de negocio (checklist 27/08): un ADMINISTRATIVO solo puede crear usuarios
  // Administrativo. Un GERENTE puede crear ambos. El backend valida lo mismo.
  const rolSelect = document.getElementById('usu-rol');
  rolSelect.innerHTML = getRol() === 'GERENTE'
    ? '<option value="GERENTE">Gerente</option><option value="ADMINISTRATIVO">Administrativo</option>'
    : '<option value="ADMINISTRATIVO">Administrativo</option>';
  rolSelect.value = 'ADMINISTRATIVO';
  rolSelect.disabled = false;
  document.getElementById('usu-rol-hint').textContent = getRol() === 'GERENTE'
    ? 'El alta de usuarios con rol Médico se hace desde el módulo Médicos.'
    : 'Como Administrativo solo podés crear usuarios Administrativo. El alta de Médicos se hace desde el módulo Médicos.';
  openModal('modal-usuario');
});

function editarUsuario(id) {
  const u = usuariosData.find(x => x.idUsuario === id);
  if (!u) return;
  editingUsuarioId = id;
  document.getElementById('modal-usuario-title').textContent = 'Editar Usuario';
  document.getElementById('usu-email').value = u.email || '';
  // La contraseña no se edita desde acá — no existe endpoint de cambio de password ajeno.
  document.getElementById('usu-password-group').classList.add('hidden');

  const rolSelect = document.getElementById('usu-rol');
  if (!['GERENTE', 'ADMINISTRATIVO'].includes(u.rol)) {
    // Usuario con rol Médico: no editable como rol desde este modal (se gestiona en Médicos).
    rolSelect.innerHTML = `<option value="${u.rol}">${ROL_LABELS[u.rol] || u.rol}</option>`;
  } else if (rolSelect.options.length < 2 || !rolSelect.querySelector(`option[value="${u.rol}"]`)) {
    rolSelect.innerHTML = `<option value="GERENTE">Gerente</option><option value="ADMINISTRATIVO">Administrativo</option>`;
  }
  rolSelect.value = u.rol;

  const esGerente = getRol() === 'GERENTE';
  rolSelect.disabled = !esGerente || u.rol === 'MEDICO';
  document.getElementById('usu-rol-hint').textContent = esGerente
    ? 'Solo un Gerente puede modificar el rol de un usuario.'
    : 'Tu usuario no tiene permiso para modificar roles — solo un Gerente puede hacerlo.';

  openModal('modal-usuario');
}

async function guardarUsuario() {
  const btn = document.getElementById('btn-guardar-usuario');
  const email = document.getElementById('usu-email').value.trim();
  const rol = document.getElementById('usu-rol').value;

  if (!email) {
    Toast.error('Ingresá un email.');
    return;
  }

  btn.disabled = true;
  try {
    if (editingUsuarioId) {
      const payload = { email };
      if (!document.getElementById('usu-rol').disabled) {
        payload.rol = rol;
      }
      await Api.updateUsuario(editingUsuarioId, payload);
      Toast.success('Usuario actualizado.');
    } else {
      const password = document.getElementById('usu-password').value;
      if (!password || password.length < 6) {
        Toast.error('La contraseña debe tener al menos 6 caracteres.');
        btn.disabled = false;
        return;
      }
      await Api.createUsuario({ email, password, rol });
      Toast.success('Usuario registrado.');
    }
    closeModal('modal-usuario');
    loadUsuarios();
  } catch (e) {
    Toast.error(e.message);
  } finally {
    btn.disabled = false;
  }
}

async function bajaUsuario(id) {
  if (!confirm('¿Dar de baja este usuario? Dejará de poder iniciar sesión.')) return;
  try {
    await Api.deleteUsuario(id);
    Toast.success('Usuario dado de baja.');
    loadUsuarios();
  } catch (e) {
    Toast.error(e.message);
  }
}

async function reactivarUsuarioAccion(id) {
  try {
    await Api.reactivarUsuario(id);
    Toast.success('Usuario reactivado.');
    loadUsuarios();
  } catch (e) {
    Toast.error(e.message);
  }
}
