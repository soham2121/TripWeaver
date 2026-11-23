import { useEffect, useId, useState } from 'react'
import { placesApi } from '../../api/places'
import type { Suggestion } from '../../types/place'
import { label } from '../../utils/format'

interface Props {
  value: string
  onChange: (v: string) => void
  onPick: (s: Suggestion) => void
  placeholder?: string
  /** Destination catalogues to search (default Goa). */
  destinations?: string[]
  /** Only suggest individual places (no categories or regions). */
  placesOnly?: boolean
  label?: string
}

/** Text input with Trie-backed suggestions from the API (debounced). */
export function Autocomplete({
  value,
  onChange,
  onPick,
  placeholder,
  destinations = ['goa'],
  placesOnly = false,
  label: aria = 'Search places',
}: Props) {
  const dests = destinations.join(',')
  const [items, setItems] = useState<Suggestion[]>([])
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const listId = useId()

  useEffect(() => {
    const q = value.trim()
    if (q.length < 2) {
      setItems([])
      return
    }
    let live = true
    const t = setTimeout(() => {
      Promise.all(dests.split(',').map((d) => placesApi.autocomplete(q, 8, d)))
        .then((rs) =>
          rs
            .flat()
            .filter((x) => !placesOnly || x.type === 'PLACE')
            .filter(
              (x, i, a) => a.findIndex((y) => y.type === x.type && y.value === x.value) === i,
            ),
        )
        .then((r) => live && setItems(r))
        .catch(() => live && setItems([]))
    }, 150)
    return () => {
      live = false
      clearTimeout(t)
    }
  }, [value, dests, placesOnly])

  const pick = (s: Suggestion) => {
    setOpen(false)
    setActive(-1)
    onPick(s)
  }

  return (
    <div className="relative">
      <input
        role="combobox"
        aria-expanded={open && items.length > 0}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-label={aria}
        className="w-full rounded border border-zinc-300 px-3 py-2"
        placeholder={placeholder}
        value={value}
        onChange={(e) => {
          onChange(e.target.value)
          setOpen(true)
          setActive(-1)
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 120)}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown') setActive((a) => Math.min(items.length - 1, a + 1))
          else if (e.key === 'ArrowUp') setActive((a) => Math.max(-1, a - 1))
          else if (e.key === 'Enter' && active >= 0) {
            e.preventDefault()
            pick(items[active])
          } else if (e.key === 'Escape') setOpen(false)
        }}
      />
      {open && items.length > 0 && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-20 mt-1 max-h-80 w-full overflow-auto rounded border border-zinc-200 bg-white shadow"
        >
          {items.map((s, i) => (
            <li
              key={s.type + s.value}
              role="option"
              aria-selected={i === active}
              className={`cursor-pointer px-3 py-2 text-sm ${i === active ? 'bg-teal-50' : 'hover:bg-zinc-50'}`}
              onMouseDown={(e) => {
                e.preventDefault()
                pick(s)
              }}
            >
              <span className="font-medium">{s.label}</span>{' '}
              <span className="text-xs text-zinc-500">
                {s.type === 'PLACE' ? label(s.detail) : s.type.toLowerCase()}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
