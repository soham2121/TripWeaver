import { BudgetTable } from '../../components/BudgetTable'
import { PrintSheet } from '../../components/PrintSheet'
import type { Itinerary } from '../../types/itinerary'
import { dayEntries } from './dayEntries'

const hhmm = (t: string) => t.slice(0, 5)

/** Print-only copy of an itinerary: every day, no map, Google Maps links kept. */
export function ItineraryPrint({ it, title }: { it: Itinerary; title: string }) {
  return (
    <PrintSheet title={title}>
      <ItineraryDaysPrint it={it} />
    </PrintSheet>
  )
}

/**
 * The days and budget of an itinerary for a print sheet. `dayLabel` names the days ('Stay day' inside a road
 * trip, so they do not read as driving days).
 */
export function ItineraryDaysPrint({
  it,
  dayLabel = 'Day',
  budgetTitle = 'Budget',
}: {
  it: Itinerary
  dayLabel?: string
  budgetTitle?: string
}) {
  return (
    <>
      {it.days.map((d) => (
        <div key={d.day} className="mb-4 break-inside-avoid">
          <h2 className="mb-1 text-base font-semibold">
            {dayLabel} {d.day} · {d.dayOfWeek[0] + d.dayOfWeek.slice(1).toLowerCase()} {d.date} ·{' '}
            {hhmm(d.start)}–{hhmm(d.end)}
          </h2>
          <ul className="ml-4 list-disc">
            {dayEntries(d).map((e) =>
              e.stop ? (
                <li key={e.stop.place.id}>
                  {hhmm(e.stop.start)}–{hhmm(e.stop.end)} {e.stop.place.name}
                  {e.stop.travelMinutes > 0 && (
                    <span className="text-zinc-500"> · {e.stop.travelMinutes} min travel</span>
                  )}
                </li>
              ) : (
                <li key={'meal' + e.meal!.label}>
                  {e.meal!.label} {hhmm(e.meal!.start)}–{hhmm(e.meal!.end)}
                  {e.meal!.place ? ` at ${e.meal!.place}` : ' (break)'}
                </li>
              ),
            )}
            {d.returnToBase && <li>{d.returnTravelMinutes} min back to base</li>}
          </ul>
          {d.navigationUrls.map((u, i) => (
            <a key={u} href={u} className="mr-3 text-teal-700 underline">
              Open {dayLabel.toLowerCase()} {d.day} in Google Maps
              {d.navigationUrls.length > 1 ? ` (part ${i + 1})` : ''}
            </a>
          ))}
        </div>
      ))}
      <div className="break-inside-avoid">
        <h2 className="mb-1 text-base font-semibold">{budgetTitle}</h2>
        <BudgetTable budget={it.budget} />
      </div>
      <p className="mt-3 text-xs text-zinc-500">{it.disclaimer}</p>
    </>
  )
}
