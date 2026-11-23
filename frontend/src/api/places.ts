import { api } from './client'
import type { Destination, Page, PlaceDetail, PlaceSummary, Suggestion } from '../types/place'

export interface SearchParams {
  q?: string
  interest?: string
  category?: string
  region?: string
  activitiesOnly?: boolean
  limit?: number
  offset?: number
}

function qs(params: Record<string, string | number | boolean | undefined>): string {
  const s = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== '') s.set(k, String(v))
  return s.toString()
}

export const placesApi = {
  search: (p: SearchParams) =>
    api<Page<PlaceSummary>>(`/places/search?${qs({ ...p, destination: 'goa' })}`),
  autocomplete: (q: string, limit = 8) =>
    api<Suggestion[]>(`/places/autocomplete?${qs({ q, limit, destination: 'goa' })}`),
  get: (id: string) => api<PlaceDetail>(`/places/${encodeURIComponent(id)}`),
  destinations: () => api<Destination[]>('/destinations'),
}
