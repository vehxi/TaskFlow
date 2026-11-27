import { api } from './taskflow.ts';
import type {
  Mutation, Page, ProjectMember, ReferenceData, TaskComment, TaskDetail, TaskFields, TaskFilterOptions,
  TaskStatus, TaskSummary, TaskTimeEntry, TaskTimePage,
} from './types.ts';

export const getTasks = (filters: string, signal: AbortSignal) =>
  api.request<Page<TaskSummary>>(`/api/tasks?${filters}`, { signal });
export const getTaskFilters = (signal: AbortSignal) =>
  api.request<TaskFilterOptions>('/api/tasks/filter-options', { signal });
export const getTask = (id: string, signal: AbortSignal) =>
  api.request<TaskDetail>(`/api/tasks/${encodeURIComponent(id)}`, { signal });
export const createTask = (projectId: number, fields: TaskFields) =>
  api.json<Mutation<TaskDetail>>(`/api/projects/${projectId}/tasks`, 'POST', fields);
export const updateTask = (id: number, fields: TaskFields, expectedVersion: number) =>
  api.json<Mutation<TaskDetail>>(`/api/tasks/${id}`, 'PUT', { ...fields, expectedVersion });
export const getReferenceData = (signal: AbortSignal) =>
  api.request<ReferenceData>('/api/reference-data', { signal });

export async function getTaskAssignees(projectId: number, signal: AbortSignal) {
  const result = new Map<number, ProjectMember>();
  let page = 0;
  let totalPages: number;
  do {
    const data = await api.request<Page<ProjectMember>>(
      `/api/projects/${projectId}/members?assignableOnly=true&size=100&page=${page}`, { signal });
    for (const user of data.items) result.set(user.id, user);
    totalPages = data.totalPages;
    page += 1;
  } while (page < totalPages);
  return [...result.values()];
}

export const setTaskStatus = (id: number, targetStatus: TaskStatus, expectedVersion: number, assigneeId?: number) =>
  api.json<Mutation<TaskDetail>>(`/api/tasks/${id}/status`, 'POST', { targetStatus, expectedVersion, assigneeId });
export const getTaskComments = (id: number, page: number, signal: AbortSignal) =>
  api.request<Page<TaskComment>>(`/api/tasks/${id}/comments?page=${page}`, { signal });
export const addTaskComment = (id: number, body: string, expectedTaskVersion: number) =>
  api.json<Mutation<{ comment: TaskComment; taskVersion: number }>>(
    `/api/tasks/${id}/comments`, 'POST', { body, expectedTaskVersion });
export const getTaskTime = (id: number, page: number, signal: AbortSignal) =>
  api.request<TaskTimePage>(`/api/tasks/${id}/time-entries?page=${page}`, { signal });
export const addTaskTime = (id: number, workDate: string, minutes: number, expectedTaskVersion: number) =>
  api.json<Mutation<{ timeEntry: TaskTimeEntry; taskVersion: number }>>(
    `/api/tasks/${id}/time-entries`, 'POST', { workDate, minutes, expectedTaskVersion });
