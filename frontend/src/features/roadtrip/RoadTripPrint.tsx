import { BudgetTable } from '../../components/BudgetTable'
import { PrintSheet } from '../../components/PrintSheet'
import type { RoadTrip, RoadTripDay } from '../../types/roadtrip'
import { ItineraryDaysPrint } from '../plan/ItineraryPrint'
import { hhmm, hours, ITEM_TEXT, stayKind } from './format'

/** Print-only copy of a road trip: every day's timeline, overnight stay and Google Maps links; no map. */
export function RoadTripPrint({ trip, title }: { trip: RoadTrip; title: string }) {
  const way = trip.returnTrip?.options.find((o) => o.number === trip.returnTrip!.chosen)
  return (
    <PrintSheet title={title}>
      <DrivePrint days={trip.days} />
      <div className="break-inside-avoid">
        <h2 className="mb-1 text-base font-semibold">Budget</h2>
        <BudgetTable budget={trip.budget} />
      </div>
      <p className="mt-3 text-xs text-zinc-500">{trip.disclaimer}</p>
      {trip.stay && (
        <div className="mt-4">
          <h2 className="mb-1 text-base font-semibold">
            Your stay in {trip.segments.at(-1)?.to ?? 'the destination'} · {trip.stay.firstDay} to{' '}
            {trip.stay.lastDay}
          </h2>
          <p className="mb-3">
            {trip.stay.nights} night(s) there · priced in the trip budget above
          </p>
          {trip.stay.note && <p className="mb-3">{trip.stay.note}</p>}
          {trip.stay.itinerary && (
            <ItineraryDaysPrint
              it={trip.stay.itinerary}
              dayLabel="Stay day"
              budgetTitle="Budget for the stay"
            />
          )}
        </div>
      )}
      {way && way.plan && (
        <div className="mt-4">
          <h2 className="mb-2 break-after-avoid text-base font-semibold">Way home: {way.label}</h2>
          <DrivePrint days={way.plan.days} label="Return day" />
          <div className="break-inside-avoid">
            <h2 className="mb-1 text-base font-semibold">Budget for the way home</h2>
            <BudgetTable budget={way.plan.budget} />
          </div>
        </div>
      )}
    </PrintSheet>
  )
}

/** Driving days for print: timeline, overnight and Maps links. */
function DrivePrint({ days, label = 'Day' }: { days: RoadTripDay[]; label?: string }) {
  return (
    <>
      {days.map((d) => (
        <div key={d.day} className="mb-4 break-inside-avoid">
          <h2 className="mb-1 text-base font-semibold">
            {label} {d.day} · {d.date} · {hours(d.drivingMinutes)} driving, {Math.round(d.km)} km
          </h2>
          <ul className="ml-4 list-disc">
            {d.items.map((i, k) => (
              <li key={k}>
                {hhmm(i.start)} {ITEM_TEXT[i.type](i)}
                {i.note && <span className="text-zinc-500"> · {i.note}</span>}
              </li>
            ))}
          </ul>
          {d.overnight && (
            <p>
              {stayKind(d.overnight)}: {d.overnight.name}
              <span className="text-zinc-500"> · {d.overnight.note}</span>
            </p>
          )}
          {d.navigationUrls.map((u, k) => (
            <a key={u} href={u} className="mr-3 text-teal-700 underline">
              Open {label.toLowerCase()} {d.day} in Google Maps
              {d.navigationUrls.length > 1 ? ` (part ${k + 1})` : ''}
            </a>
          ))}
        </div>
      ))}
    </>
  )
}
