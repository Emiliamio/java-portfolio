/**
 * AuditVault — 登录逻辑
 * 登录成功后跳回来源页；失败提示错误。
 */
(function () {
  const form = document.getElementById('loginForm');
  const errorEl = document.getElementById('loginError');
  const btn = document.getElementById('loginBtn');

  // 跳转目标：支持 ?redirect= 参数，默认首页
  const params = new URLSearchParams(window.location.search);
  const redirect = params.get('redirect') || 'index.html';

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;

    if (!username || !password) {
      showError('请输入用户名和密码');
      return;
    }

    btn.disabled = true;
    btn.textContent = '登录中…';
    errorEl.style.display = 'none';

    try {
      const resp = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });
      const data = await resp.json();

      if (resp.ok && data.success) {
        window.location.href = redirect;
      } else {
        showError(data.message || '登录失败');
      }
    } catch (err) {
      showError('无法连接服务器，请确认服务已启动');
    } finally {
      btn.disabled = false;
      btn.textContent = '登 录';
    }
  });

  function showError(msg) {
    errorEl.textContent = msg;
    errorEl.style.display = 'block';
  }
})();
