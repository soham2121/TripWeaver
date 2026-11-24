import type { Budget } from '../types/itinerary'
import type { Confidence } from '../types/place'
import { label } from '../utils/format'
import { ConfidenceBadge } from './ConfidenceBadge'

const inr = (n: number) => '₹' + n.toLocaleString('en-IN', { maximumFractionDigits: 0 })

const amount = (min: number | null, max: number | null) =>
  min == null || max == null ? 'not known' : min === max ? inr(max) : `${inr(min)}–${inr(max)}`

/** Cost lines with their confidence. Unknown amounts are never shown as ₹0. */
export function BudgetTable({ budget }: { budget: Budget }) {
  return (
    <div className="overflow-x-auto">
      <table aria-label="Budget breakdown" className="w-full min-w-[32rem] text-left text-sm">
        <thead className="text-xs text-zinc-500">
          <tr>
            <th className="py-1 font-medium">Item</th>
            <th className="font-medium">Type</th>
            <th className="font-medium">Amount</th>
            <th className="font-medium">Source</th>
          </tr>
        </thead>
        <tbody>
          {budget.lines.map((l, i) => (
            <tr key={l.category + l.item + i} className="border-t border-zinc-100 align-top">
              <td className="py-1">{l.item}</td>
              <td className="text-zinc-600">{label(l.category.toLowerCase())}</td>
              <td>
                {amount(l.min, l.max)}
                <div className="text-xs text-zinc-500">
                  {[l.basis, l.note].filter(Boolean).join(' · ')}
                </div>
              </td>
              <td>
                <ConfidenceBadge confidence={l.confidence as Confidence} />
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot className="border-t border-zinc-300 text-xs text-zinc-700">
          <tr>
            <td colSpan={4} className="py-1">
              Known total {amount(budget.knownMin, budget.knownMax)} for {budget.partySize}{' '}
              {budget.partySize === 1 ? 'person' : 'people'} (up to {inr(budget.knownMaxPerPerson)}{' '}
              per person)
              {budget.unknownItems.length > 0 &&
                ` · ${budget.unknownItems.length} item(s) not known, so the total is incomplete`}
              {budget.limit != null &&
                ` · ${label(budget.strictness?.toLowerCase() ?? '')} limit ${inr(budget.limit)} ${label(
                  budget.limitBasis?.toLowerCase() ?? '',
                )}`}{' '}
              · status {label(budget.status.toLowerCase())}
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}
