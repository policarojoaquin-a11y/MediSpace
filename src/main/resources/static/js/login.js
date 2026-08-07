// ---- Login JS ----
(function () {
  // Redirect if already logged in
  if (Api.getToken()) { window.location.href = '/app.html'; return; }

  const form     = document.getElementById('login-form');
  const btnLogin = document.getElementById('btn-login');
  const btnText  = document.getElementById('btn-text');
  const btnArrow = document.getElementById('btn-arrow');
  const spinner  = document.getElementById('btn-spinner');
  const errBox   = document.getElementById('login-error');
  const errMsg   = document.getElementById('login-error-msg');

  // Toggle password visibility
  document.getElementById('toggle-pass')?.addEventListener('click', () => {
    const pw = document.getElementById('password');
    pw.type = pw.type === 'password' ? 'text' : 'password';
  });

  function setLoading(loading) {
    btnLogin.disabled = loading;
    btnText.classList.toggle('hidden', loading);
    btnArrow.classList.toggle('hidden', loading);
    spinner.classList.toggle('hidden', !loading);
  }

  function showError(msg) {
    errMsg.textContent = msg;
    errBox.classList.remove('hidden');
  }

  function hideError() {
    errBox.classList.add('hidden');
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideError();

    const email    = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;

    if (!email || !password) {
      showError('Completá el email y la contraseña para continuar.');
      return;
    }

    setLoading(true);
    try {
      const data = await Api.login(email, password);
      // Expected: { token: '...', email: '...', rol: 'GERENTE', nombre: '...' }
      Api.setToken(data.token);
      localStorage.setItem('ms_user', JSON.stringify({
        email: data.email,
        rol:   data.rol,
        nombre: data.nombre || data.email,
        id:    data.id,
      }));
      window.location.href = '/app.html';
    } catch (err) {
      showError(err.message || 'Email o contraseña incorrectos.');
    } finally {
      setLoading(false);
    }
  });
})();
