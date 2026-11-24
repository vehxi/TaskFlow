import { ApiClient } from './client.ts';
import type {
  CreateUser, CurrentUser, MembershipChange, Mutation, Page, ProjectDetails, ProjectMember,
  ProjectMembers, ProjectSummary, UpdateUser, UserAccount,
} from './types.ts';

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

export const getProjects = (page: number, signal: AbortSignal) =>
  api.request<Page<ProjectSummary>>(`/api/projects?page=${page}`, { signal });
export const getProject = (id: string, signal: AbortSignal) =>
  api.request<ProjectSummary>(`/api/projects/${encodeURIComponent(id)}`, { signal });
export const createProject = (body: ProjectDetails) =>
  api.json<Mutation<ProjectSummary>>('/api/projects', 'POST', body);
export const updateProject = (id: number, body: ProjectDetails, expectedVersion: number) =>
  api.json<Mutation<ProjectSummary>>(`/api/projects/${id}`, 'PUT', { ...body, expectedVersion });
export const getMembers = (id: number, page: number, signal: AbortSignal) =>
  api.request<ProjectMembers>(`/api/projects/${id}/members?page=${page}`, { signal });
export const getCandidates = (id: number, page: number, signal: AbortSignal) =>
  api.request<Page<ProjectMember>>(`/api/assignable-users?projectId=${id}&page=${page}`, { signal });
export const addMember = (id: number, userId: number, expectedProjectVersion: number) =>
  api.json<Mutation<MembershipChange>>(`/api/projects/${id}/members`, 'POST', { userId, expectedProjectVersion });
export const removeMember = (id: number, userId: number, expectedProjectVersion: number) =>
  api.request<Mutation<MembershipChange>>(
    `/api/projects/${id}/members/${userId}?expectedProjectVersion=${expectedProjectVersion}`, { method: 'DELETE' });
