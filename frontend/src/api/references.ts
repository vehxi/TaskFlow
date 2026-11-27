import { api } from './taskflow.ts';
import type { Mutation, ReferenceData, ReferenceValue } from './types.ts';

export type ReferenceCatalog = keyof ReferenceData;
const endpoints = { types: '/api/task-types', priorities: '/api/task-priorities' } satisfies Record<ReferenceCatalog, string>;

export const getReferenceData = (signal: AbortSignal) =>
  api.request<ReferenceData>('/api/reference-data', { signal });
export const getReferenceValue = (catalog: ReferenceCatalog, id: number) =>
  api.request<ReferenceValue>(`${endpoints[catalog]}/${id}`);
export const createReferenceValue = (catalog: ReferenceCatalog, name: string) =>
  api.json<Mutation<ReferenceValue>>(endpoints[catalog], 'POST', { name });
export const updateReferenceValue = (catalog: ReferenceCatalog, id: number, name: string,
  active: boolean, expectedVersion: number) =>
  api.json<Mutation<ReferenceValue>>(`${endpoints[catalog]}/${id}`, 'PUT', { name, active, expectedVersion });
