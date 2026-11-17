import { ApiClient } from './client.ts';
import type { CreateUser, CurrentUser, Mutation, Page, UpdateUser, UserAccount } from './types.ts';

export const api = new ApiClient(undefined,
  () => window.dispatchEvent(new Event('taskflow:session-expired')),
  () => window.dispatchEvent(new Event('taskflow:permissions-changed')));
export const getCurrentUser = () => api.request<CurrentUser>('/api/me');
export async function signIn(email: string, password: string) {
  await api.request<void>('/api/session', {
    method: 'POST',
    body: new URLSearchParams({ email, password }),
  });
  await api.refreshCsrf();
  return getCurrentUser();
}
export async function signOut() {
  await api.request<void>('/api/session/logout', { method: 'POST' });
  api.resetCsrf();
}
export const getUsers = (page = 0, signal?: AbortSignal) =>
  api.request<Page<UserAccount>>(`/api/users?page=${page}&size=20`, { signal });
export const getUser = (id: string, signal?: AbortSignal) =>
  api.request<UserAccount>(`/api/users/${encodeURIComponent(id)}`, { signal });
export const createUser = (body: CreateUser) => api.json<Mutation<UserAccount>>('/api/users', 'POST', body);
export const updateUser = (id: string, body: UpdateUser) =>
  api.json<Mutation<UserAccount>>(`/api/users/${encodeURIComponent(id)}`, 'PUT', body);
