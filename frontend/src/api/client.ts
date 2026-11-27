import type { ApiErrorBody, CsrfToken } from './types.ts';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string[]>;
  readonly requestId: string;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code;
    this.fieldErrors = body.fieldErrors;
    this.requestId = body.requestId;
  }
}

// The token stays in memory. An unsafe operation is sent once, even after a conflict.
export class ApiClient {
  private csrf: Promise<CsrfToken> | undefined;
  private readonly transport: typeof fetch;
  private readonly sessionExpired: () => void;
  private readonly permissionsChanged: () => void;

  constructor(transport: typeof fetch = globalThis.fetch.bind(globalThis), sessionExpired = () => {}, permissionsChanged = () => {}) {
    this.transport = transport;
    this.sessionExpired = sessionExpired;
    this.permissionsChanged = permissionsChanged;
  }

  resetCsrf() { this.csrf = undefined; }

  async refreshCsrf(): Promise<void> {
    this.resetCsrf();
    await this.getCsrf();
  }

  async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const response = await this.send(path, 'application/json', options);
    if (response.status === 204) return undefined as T;
    if (!response.headers.get('Content-Type')?.includes('application/json')) {
      throw new Error('Сервер вернул неожиданный ответ. Обновите страницу.');
    }
    return response.json() as Promise<T>;
  }

  async download(path: string, contentType: string, signal?: AbortSignal): Promise<Blob> {
    const response = await this.send(path, `${contentType}, application/json`, { signal });
    if (response.headers.get('Content-Type')?.split(';')[0] !== contentType) {
      throw new Error('Сервер не вернул файл нужного формата.');
    }
    return response.blob();
  }

  private async send(path: string, accept: string, options: RequestInit): Promise<Response> {
    if (!path.startsWith('/api/') || path.startsWith('//') || path.includes('\\')) {
      throw new Error('Only relative API paths are supported.');
    }
    const method = (options.method ?? 'GET').toUpperCase();
    const headers = new Headers(options.headers);
    headers.set('Accept', accept);
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      const csrf = await this.getCsrf();
      headers.set(csrf.headerName, csrf.token);
    }
    let response: Response;
    try {
      response = await this.transport(path, { ...options, method, headers, credentials: 'same-origin', cache: 'no-store' });
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') throw error;
      throw new Error('Нет связи с сервером. Проверьте подключение и состояние приложения.');
    }
    if (!response.ok) {
      const failure = await this.failure(response);
      if (response.status === 401) {
        this.resetCsrf();
        if (failure.code !== 'INVALID_CREDENTIALS') this.sessionExpired();
      }
      if (failure.code === 'ACCESS_DENIED') this.permissionsChanged();
      if (failure.code === 'CSRF_INVALID') this.resetCsrf();
      throw failure;
    }
    return response;
  }

  json<T>(path: string, method: 'POST' | 'PUT', body: unknown): Promise<T> {
    return this.request<T>(path, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  }

  private getCsrf(): Promise<CsrfToken> {
    if (!this.csrf) {
      const pending = this.request<CsrfToken>('/api/csrf');
      this.csrf = pending;
      // Do not retain a failed token request; the next explicit action can recover.
      void pending.catch(() => { if (this.csrf === pending) this.resetCsrf(); });
    }
    return this.csrf;
  }

  private async failure(response: Response): Promise<ApiError> {
    let body: Partial<ApiErrorBody> = {};
    try {
      const decoded: unknown = await response.json();
      if (decoded && typeof decoded === 'object') body = decoded as Partial<ApiErrorBody>;
    } catch { /* A proxy may return plain text. */ }
    return new ApiError(response.status, {
      code: typeof body.code === 'string' ? body.code : 'HTTP_ERROR',
      message: typeof body.message === 'string' ? body.message : 'Не удалось выполнить запрос.',
      fieldErrors: body.fieldErrors && typeof body.fieldErrors === 'object' ? body.fieldErrors : {},
      requestId: typeof body.requestId === 'string' ? body.requestId : '',
    });
  }
}
