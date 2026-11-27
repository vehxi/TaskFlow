import type { TaskStatus } from '../api/types.ts';

export const taskStatusNames: Record<TaskStatus, string> = {
  NEW: 'Новая',
  IN_PROGRESS: 'В работе',
  DONE: 'Выполнена',
  CANCELLED: 'Отменена',
};

const dateFormat = new Intl.DateTimeFormat('ru-RU', {
  day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC',
});

export const displayDueDate = (date: string) => dateFormat.format(new Date(date + 'T00:00:00Z'));
export const isOpenTask = (status: TaskStatus) => status === 'NEW' || status === 'IN_PROGRESS';
