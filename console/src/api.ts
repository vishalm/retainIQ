const API = '/v1/manage';

let token: string | null = localStorage.getItem('token');

export function setToken(t: string) {
  token = t;
  localStorage.setItem('token', t);
}

export function clearToken() {
  token = null;
  localStorage.removeItem('token');
}

export function getToken() { return token; }

async function request(path: string, options: RequestInit = {}) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {})
  };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${API}${path}`, { ...options, headers });
  if (res.status === 204) return null;
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: { message: res.statusText } }));
    throw new Error(err.error?.message || res.statusText);
  }
  return res.json();
}

export const api = {
  login: (email: string, password: string) =>
    request('/login', { method: 'POST', body: JSON.stringify({ email, password }) }),

  // Dashboard
  getStats: (tenantId?: string) =>
    request(`/dashboard/stats${tenantId ? `?tenant_id=${tenantId}` : ''}`),

  // Tenants
  listTenants: () => request('/tenants'),
  getTenant: (id: string) => request(`/tenants/${id}`),
  createTenant: (data: any) => request('/tenants', { method: 'POST', body: JSON.stringify(data) }),
  updateTenant: (id: string, data: any) => request(`/tenants/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  activateTenant: (id: string) => request(`/tenants/${id}/activate`, { method: 'POST' }),
  suspendTenant: (id: string) => request(`/tenants/${id}/suspend`, { method: 'POST' }),
  testBss: (id: string) => request(`/tenants/${id}/test-bss`, { method: 'POST' }),
  regenerateCredentials: (id: string) => request(`/tenants/${id}/regenerate-credentials`, { method: 'POST' }),

  // Users
  listUsers: (tenantId?: string) =>
    request(`/users${tenantId ? `?tenant_id=${tenantId}` : ''}`),
  createUser: (data: any) => request('/users', { method: 'POST', body: JSON.stringify(data) }),
  updateUser: (id: string, data: any) => request(`/users/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteUser: (id: string) => request(`/users/${id}`, { method: 'DELETE' }),
};
