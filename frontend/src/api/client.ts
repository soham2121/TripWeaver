export interface ApiErrorBody {
  errorCode: string
  message: string
  details?: Record<string, unknown>
}

export class ApiError extends Error {
  readonly status: number
  readonly errorCode: string
  readonly details: Record<string, unknown>

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.status = status
    this.errorCode = body.errorCode
    this.details = body.details ?? {}
  }
}

/** JSON fetch against the TripWeaver API. Throws ApiError on non-2xx responses. */
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`/api/v1${path}`, {
    credentials: 'include',
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  })
  if (!res.ok) {
    let body: ApiErrorBody = { errorCode: 'HTTP_' + res.status, message: res.statusText }
    try {
      body = await res.json()
    } catch {
      // non-JSON error body: keep generic
    }
    throw new ApiError(res.status, body)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
