/**
 * Nexus AI — 登录逻辑（与 AuditVault 共用账号）
 */
(function () {
  const form = document.getElementById('loginForm');
  const errorEl = document.getElementById('loginError');
  const btn = document.getElementById('loginBtn');

  const params = new URLSearchParams(window.location.search);
  const redirect = params.get('redirect') || 'index.html';

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;

    if (!username || !password) { showError('请输入用户名和密码'); return; }

    btn.disabled = true; btn.textContent = '登录中…'; errorEl.style.display = 'none';

    try {
      const resp = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });
      const data = await resp.json();
      if (resp.ok && data.code === 200) {
        window.location.href = redirect;
      } else {
        showError(data.message || '登录失败');
      }
    } catch (err) {
      showError('无法连接服务器，请确认服务已启动');
    } finally {
      btn.disabled = false; btn.textContent = '登 录';
    }
  });

  function showError(msg) {
    errorEl.textContent = msg;
    errorEl.style.display = 'block';
  }
})();
