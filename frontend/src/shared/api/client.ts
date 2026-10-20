export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

// POST/PATCH APIs and CSRF token handling are added with the authentication feature.
export async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`/api/v1${path}`, {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
    signal,
  });
  if (!response.ok) throw new ApiError(response.status, `서버 응답 오류 (${response.status})`);
  return response.json() as Promise<T>;
}
