import {getAccessToken} from '../auth/keycloak'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

/** fetch() against the backend with the current user's bearer JWT attached. */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = await getAccessToken()
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')

  const response = await fetch(`${API_BASE_URL}${path}`, {...init, headers})
  if (!response.ok) throw new ApiError(response.status, `${response.status} ${response.statusText}`)
  // Some endpoints answer 200 with an empty body
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
