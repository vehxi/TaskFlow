export type RoleCode = 'ADMIN' | 'MANAGER' | 'EXECUTOR';
export interface CurrentUser { id: number; name: string; email: string; roles: RoleCode[] }
export interface UserAccount extends CurrentUser { active: boolean; version: number }
export interface Page<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface Mutation<T> { data: T; notificationStatus: 'NOT_REQUIRED' | 'SENT' | 'FAILED'; warning?: string }
export interface CreateUser { name: string; email: string; initialPassword: string; roles: RoleCode[] }
export interface UpdateUser { name: string; email: string; roles: RoleCode[]; active: boolean; expectedVersion: number }
export interface ProjectDetails { name: string; description: string }
export interface ProjectSummary extends ProjectDetails { id: number; managerId: number; version: number }
export interface ProjectMember { id: number; name: string }
export interface MembershipChange { projectVersion: number }
export type ProjectMembers = Page<ProjectMember> & MembershipChange;
export interface CsrfToken { token: string; headerName: string }
export interface ApiErrorBody { code: string; message: string; fieldErrors: Record<string, string[]>; requestId: string }

export type TaskStatus = 'NEW' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED';
export interface TaskFields {
  title: string;
  description: string;
  assigneeId: number;
  dueDate: string;
  typeId: number;
  priorityId: number;
}
export interface TaskSummary {
  id: number;
  projectId: number;
  projectName: string;
  title: string;
  dueDate: string;
  status: TaskStatus;
  assigneeId: number;
  assigneeName: string;
  version: number;
}
export interface TaskDetail extends TaskSummary, TaskFields {
  managerId: number;
  authorId: number;
  authorName: string;
  typeName: string;
  priorityName: string;
}
export interface TaskFilterOptions { projects: ProjectMember[]; assignees: ProjectMember[] }
export interface ReferenceValue extends ProjectMember { active: boolean; version: number }
export interface ReferenceData { types: ReferenceValue[]; priorities: ReferenceValue[] }

export interface TaskComment {
  id: number;
  authorId: number;
  authorName: string;
  body: string;
  createdAt: string;
}
export interface TaskTimeEntry {
  id: number;
  authorId: number;
  authorName: string;
  workDate: string;
  minutes: number;
  createdAt: string;
}
export interface TaskTimePage extends Page<TaskTimeEntry> {
  totalMinutes: number;
  today: string;
}
