import { api } from './taskflow.ts';
import type { Page, ProjectSummary, TaskStatus } from './types.ts';

interface ReportPerson {
  userId: number;
  name: string;
}

export interface ProjectReport {
  project: ProjectSummary;
  generatedAt: string;
  byStatus: Record<TaskStatus, number>;
  overdueCount: number;
  progressPercent: number;
  totalMinutes: number;
  tasksByAssignee: (ReportPerson & { taskCount: number })[];
  minutesByAuthor: (ReportPerson & { minutes: number })[];
}

export interface OverdueTask {
  id: number;
  title: string;
  dueDate: string;
  status: TaskStatus;
  assigneeName: string;
}

export const getProjectReport = (id: string, signal: AbortSignal) =>
  api.request<ProjectReport>(`/api/projects/${encodeURIComponent(id)}/report`, { signal });
export const getOverdueTasks = (id: string, page: number, signal: AbortSignal) =>
  api.request<Page<OverdueTask>>(`/api/projects/${encodeURIComponent(id)}/report/overdue?page=${page}`, { signal });
export const downloadProjectReport = (id: string, signal: AbortSignal) =>
  api.download(`/api/projects/${encodeURIComponent(id)}/report.xlsx`,
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', signal);
