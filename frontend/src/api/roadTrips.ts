import { api } from './client'
import type { RoadTripRequest, RoadTripResponse } from '../types/roadtrip'

export const roadTripsApi = {
  plan: (req: RoadTripRequest) =>
    api<RoadTripResponse>('/road-trips/plan', { method: 'POST', body: JSON.stringify(req) }),
}
