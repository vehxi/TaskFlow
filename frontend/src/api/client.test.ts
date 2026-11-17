import test from 'node:test';
import assert from 'node:assert/strict';
import { ApiClient, ApiError } from './client.ts';

const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), {
  status, headers: { 'Content-Type': 'application/json' },
});

test('unsafe requests fetch CSRF once and send same-origin credentials', async () => {
  const calls: { path: string; options: RequestInit }[] = [];
  const client = new ApiClient(async (path, options = {}) => {
    calls.push({ path: String(path), options });
    return path === '/api/csrf' ? json({ token: 'masked-token', headerName: 'X-CSRF-TOKEN' }) : new Response(null, { status: 204 });
  });
  await client.request('/api/session', { method: 'POST', body: new URLSearchParams({ email: 'a@example.test', password: 'secret' }) });
  await client.json('/api/users/1', 'PUT', { expectedVersion: 0 });
  assert.deepEqual(calls.map(call => call.path), ['/api/csrf', '/api/session', '/api/users/1']);
  assert.equal(new Headers(calls[1].options.headers).get('X-CSRF-TOKEN'), 'masked-token');
  assert.equal(calls[1].options.credentials, 'same-origin');
  assert.equal(calls[2].options.method, 'PUT');
});

test('a conflict never repeats a mutation and preserves field errors', async () => {
  let mutations = 0;
  const client = new ApiClient(async path => {
    if (path === '/api/csrf') return json({ token: 'token', headerName: 'X-CSRF-TOKEN' });
    mutations++;
    return json({ code: 'STALE_VERSION', message: 'Обновите карточку.', fieldErrors: {}, requestId: 'r1' }, 409);
  });
  await assert.rejects(client.json('/api/users/1', 'PUT', {}), (error: unknown) =>
    error instanceof ApiError && error.status === 409 && error.code === 'STALE_VERSION' && error.requestId === 'r1');
  assert.equal(mutations, 1);
});

test('expired sessions notify the application; invalid login does not', async () => {
  let expired = 0;
  const client = new ApiClient(async path => path === '/api/csrf'
    ? json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
    : json({ code: path === '/api/session' ? 'INVALID_CREDENTIALS' : 'AUTHENTICATION_REQUIRED', message: 'Войдите.', fieldErrors: {}, requestId: 'r' }, 401),
    () => { expired++; });
  await assert.rejects(client.request('/api/session', { method: 'POST' }));
  assert.equal(expired, 0);
  await assert.rejects(client.request('/api/me'));
  assert.equal(expired, 1);
});

test('explicit refresh obtains a new CSRF after login', async () => {
  let tokens = 0;
  const sent: string[] = [];
  const client = new ApiClient(async (path, options) => {
    if (path === '/api/csrf') return json({ token: `token-${++tokens}`, headerName: 'X-CSRF-TOKEN' });
    sent.push(new Headers(options?.headers).get('X-CSRF-TOKEN') ?? '');
    return new Response(null, { status: 204 });
  });
  await client.request('/api/session', { method: 'POST' });
  await client.refreshCsrf();
  await client.request('/api/session/logout', { method: 'POST' });
  assert.deepEqual(sent, ['token-1', 'token-2']);
});

test('a failed token fetch is recoverable by the next explicit action', async () => {
  let attempts = 0;
  const client = new ApiClient(async path => {
    if (path === '/api/csrf') {
      if (++attempts === 1) throw new Error('offline');
      return json({ token: 'new', headerName: 'X-CSRF-TOKEN' });
    }
    return new Response(null, { status: 204 });
  });
  await assert.rejects(client.request('/api/session', { method: 'POST' }));
  await client.request('/api/session', { method: 'POST' });
  assert.equal(attempts, 2);
});

test('HTML proxy errors do not become fake successful JSON', async () => {
  const client = new ApiClient(async () => new Response('<html>SPA</html>'));
  await assert.rejects(client.request('/api/me'), /неожиданный ответ/);
});

test('credentials and CSRF cannot be sent to an external URL', async () => {
  let calls = 0;
  const client = new ApiClient(async () => { calls++; return json({}); });
  await assert.rejects(client.request('https://elsewhere.test/api/users', { method: 'POST' }));
  assert.equal(calls, 0);
});

test('access denial refreshes permissions without claiming the session expired', async () => {
  let expired = 0; let permissions = 0;
  const client = new ApiClient(async () => json({ code: 'ACCESS_DENIED', message: 'Недоступно.', fieldErrors: {}, requestId: 'r' }, 403),
    () => { expired++; }, () => { permissions++; });
  await assert.rejects(client.request('/api/users'));
  assert.equal(expired, 0); assert.equal(permissions, 1);
});

test('a CSRF rejection discards the token but never retries the rejected command', async () => {
  let tokens = 0; let mutations = 0;
  const client = new ApiClient(async path => {
    if (path === '/api/csrf') return json({ token: `token-${++tokens}`, headerName: 'X-CSRF-TOKEN' });
    if (++mutations === 1) return json({ code: 'CSRF_INVALID', message: 'Повторите действие.', fieldErrors: {}, requestId: 'r' }, 403);
    return new Response(null, { status: 204 });
  });
  await assert.rejects(client.request('/api/session', { method: 'POST' }));
  assert.equal(mutations, 1);
  await client.request('/api/session', { method: 'POST' });
  assert.equal(tokens, 2); assert.equal(mutations, 2);
});
