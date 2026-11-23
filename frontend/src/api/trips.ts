import { api } from './client'
import type {
  AppUser,
  Expenses,
  HistoryEntry,
  Member,
  ShareLink,
  SharedTrip,
  Trip,
  TripKind,
  TripSuggestion,
  TripSummary,
} from '../types/trip'

const json = (method: string, body?: unknown): RequestInit => ({
  method,
  body: body === undefined ? undefined : JSON.stringify(body),
})

export const authApi = {
  me: () => api<AppUser>('/auth/me'),
  login: (username: string, password: string) =>
    api<AppUser>('/auth/login', json('POST', { username, password })),
  register: (username: string, password: string, displayName?: string) =>
    api<AppUser>('/auth/register', json('POST', { username, password, displayName })),
  logout: () => api<void>('/auth/logout', json('POST')),
}

const t = (id: string) => `/trips/${encodeURIComponent(id)}`

export const tripsApi = {
  list: () => api<TripSummary[]>('/trips'),
  create: (title: string, kind: TripKind, request: unknown, result: unknown) =>
    api<Trip>('/trips', json('POST', { title, kind, request, result })),
  get: (id: string) => api<Trip>(t(id)),
  update: (
    id: string,
    body: { version: number; title?: string; request?: unknown; result?: unknown },
  ) => api<Trip>(t(id), json('PATCH', body)),
  generate: (id: string, version: number, request?: unknown) =>
    api<Trip>(`${t(id)}/generate`, json('POST', { version, request })),
  remove: (id: string) => api<void>(t(id), json('DELETE')),
  addMember: (id: string, username: string, role: 'EDITOR' | 'VIEWER') =>
    api<Member>(`${t(id)}/members`, json('POST', { username, role })),
  changeRole: (id: string, userId: string, role: 'EDITOR' | 'VIEWER') =>
    api<void>(`${t(id)}/members/${userId}`, json('PATCH', { role })),
  removeMember: (id: string, userId: string) =>
    api<void>(`${t(id)}/members/${userId}`, json('DELETE')),
  shareLinks: (id: string) => api<ShareLink[]>(`${t(id)}/share-links`),
  createShareLink: (id: string, expiresInHours?: number) =>
    api<ShareLink>(`${t(id)}/share-links`, json('POST', { expiresInHours })),
  revokeShareLink: (id: string, linkId: string) =>
    api<void>(`${t(id)}/share-links/${linkId}`, json('DELETE')),
  suggestions: (id: string) => api<TripSuggestion[]>(`${t(id)}/suggestions`),
  suggest: (id: string, placeId: string, note?: string, day?: number) =>
    api<TripSuggestion>(`${t(id)}/suggestions`, json('POST', { placeId, note, day })),
  vote: (id: string, suggestionId: string, value: 1 | -1) =>
    api<void>(`${t(id)}/suggestions/${suggestionId}/votes`, json('POST', { value })),
  history: (id: string) => api<HistoryEntry[]>(`${t(id)}/history`),
  expenses: (id: string) => api<Expenses>(`${t(id)}/expenses`),
  addExpense: (id: string, description: string, amount: number) =>
    api<unknown>(`${t(id)}/expenses`, json('POST', { description, amount })),
  shared: (token: string) => api<SharedTrip>(`/shared/${encodeURIComponent(token)}`),
}
