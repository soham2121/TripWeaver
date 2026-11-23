import { api } from './client'
import type { ItineraryRequest, ItineraryResponse } from '../types/itinerary'

export const itinerariesApi = {
  generate: (req: ItineraryRequest) =>
    api<ItineraryResponse>('/itineraries/generate', {
      method: 'POST',
      body: JSON.stringify({ ...req, destination: 'goa' }),
    }),
}
