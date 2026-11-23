import type { CostEstimate } from '../types/place'

export function label(s: string): string {
  return s.replace(/^cuisine:/, '').replace(/_/g, ' ')
}

/** Money range. Unknown costs are never shown as ₹0. */
export function formatCost(c: Pick<CostEstimate, 'min' | 'max' | 'currency' | 'basis'>): string {
  const sym = c.currency === 'INR' ? '₹' : c.currency + ' '
  const fmt = (n: number) => sym + n.toLocaleString('en-IN', { maximumFractionDigits: 0 })
  const amount =
    c.min === c.max ? (c.min === 0 ? 'Free' : fmt(c.min)) : `${fmt(c.min)}–${fmt(c.max)}`
  const basis = c.basis === 'PER_PERSON' ? ' / person' : c.basis === 'PER_GROUP' ? ' / group' : ''
  return amount === 'Free' ? amount : amount + basis
}

export function formatDuration(min: number | null, max: number | null): string | null {
  if (min == null || max == null) return null
  const f = (m: number) => (m >= 60 ? `${+(m / 60).toFixed(1)} h` : `${m} min`)
  return min === max ? f(min) : `${f(min)}–${f(max)}`
}
