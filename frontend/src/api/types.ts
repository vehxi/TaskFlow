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
