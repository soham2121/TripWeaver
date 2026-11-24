import { useEffect, useRef, useState } from 'react'
import * as maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'

// MapLibre looks for its worker next to its own module. Vite pre-bundles the library (dev) and does not emit the
// worker (build), so that lookup fails and the map stays blank. Point it at the worker file Vite serves and emits.
maplibregl.setWorkerUrl(workerUrl)

export interface MapPoint {
  id: string
  lat: number
  lon: number
  label: string
  order?: number
}

/**
 * MapLibre style URL. Default: OpenFreeMap vector tiles built from OSM (no API key; see
 * docs/DATA_SOURCES.md). Set VITE_MAP_STYLE_URL to switch provider, e.g. a self-hosted style. Attribution comes
 * from the style and is rendered by MapLibre.
 */
export const MAP_STYLE: string =
  (import.meta.env.VITE_MAP_STYLE_URL as string | undefined) ||
  'https://tiles.openfreemap.org/styles/liberty'

/** OpenFreeMap's dark style in dark mode, unless a custom style URL is set. */
// ponytail: chosen when the map mounts; a theme switch shows on the next page, setStyle would drop the route layers.
const styleForTheme = () =>
  document.documentElement.dataset.theme === 'dark' && !import.meta.env.VITE_MAP_STYLE_URL
    ? 'https://tiles.openfreemap.org/styles/dark'
    : MAP_STYLE

interface Props {
  points: MapPoint[]
  selectedId?: string | null
  onSelect?: (id: string) => void
  line?: [number, number][]
  className?: string
}

export default function PlaceMap({ points, selectedId, onSelect, line, className }: Props) {
  const el = useRef<HTMLDivElement>(null)
  const map = useRef<maplibregl.Map | null>(null)
  const [failed, setFailed] = useState(false)
  const [ready, setReady] = useState(false)
  const onSelectRef = useRef(onSelect)
  onSelectRef.current = onSelect

  useEffect(() => {
    if (!el.current) return
    let m: maplibregl.Map
    try {
      m = new maplibregl.Map({
        container: el.current,
        style: styleForTheme(),
        center: [73.92, 15.4],
        zoom: 9,
      })
    } catch {
      setFailed(true)
      return
    }
    map.current = m
    m.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right')
    m.on('error', (e: maplibregl.ErrorEvent) => {
      if (String(e.error?.message ?? '').match(/style|Failed to fetch|NetworkError/i))
        setFailed(true)
    })
    m.on('load', () => {
      m.addSource('route', { type: 'geojson', data: emptyFc() })
      // White casing under the route keeps it legible over busy tiles.
      m.addLayer({
        id: 'route-casing',
        type: 'line',
        source: 'route',
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: { 'line-color': '#ffffff', 'line-width': 8, 'line-opacity': 0.9 },
      })
      m.addLayer({
        id: 'route',
        type: 'line',
        source: 'route',
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: { 'line-color': '#403da7', 'line-width': 4, 'line-opacity': 0.9 },
      })
      m.addSource('pts', { type: 'geojson', data: emptyFc() })
      m.addLayer({
        id: 'pts',
        type: 'circle',
        source: 'pts',
        paint: {
          'circle-radius': ['case', ['get', 'selected'], 11, ['has', 'order'], 9, 6],
          'circle-color': ['case', ['get', 'selected'], '#d06030', '#403da7'],
          'circle-stroke-width': 2,
          'circle-stroke-color': '#ffffff',
        },
      })
      m.addLayer({
        id: 'pts-order',
        type: 'symbol',
        source: 'pts',
        filter: ['has', 'order'],
        layout: {
          'text-field': ['to-string', ['get', 'order']],
          'text-size': 11,
          // One font the OpenFreeMap style serves (stacks are fetched as one combined name and 404).
          'text-font': ['Noto Sans Bold'],
          'text-allow-overlap': true,
        },
        paint: { 'text-color': '#ffffff' },
      })
      m.on('click', 'pts', (e: maplibregl.MapLayerMouseEvent) => {
        const id = e.features?.[0]?.properties?.id
        if (id) onSelectRef.current?.(String(id))
      })
      m.on('mouseenter', 'pts', () => (m.getCanvas().style.cursor = 'pointer'))
      m.on('mouseleave', 'pts', () => (m.getCanvas().style.cursor = ''))
      setReady(true)
    })
    return () => {
      m.remove()
      map.current = null
    }
  }, [])

  useEffect(() => {
    const m = map.current
    if (!m || !ready) return
    {
      const src = m.getSource('pts') as maplibregl.GeoJSONSource | undefined
      if (!src) return
      src.setData({
        type: 'FeatureCollection',
        features: points.map((p) => ({
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [p.lon, p.lat] },
          properties: {
            id: p.id,
            label: p.label,
            selected: p.id === selectedId,
            ...(p.order != null ? { order: p.order } : {}),
          },
        })),
      })
      const r = m.getSource('route') as maplibregl.GeoJSONSource | undefined
      r?.setData(
        line && line.length > 1
          ? {
              type: 'FeatureCollection',
              features: [
                {
                  type: 'Feature',
                  geometry: { type: 'LineString', coordinates: line },
                  properties: {},
                },
              ],
            }
          : emptyFc(),
      )
      if (points.length > 0) {
        const b = new maplibregl.LngLatBounds()
        points.forEach((p) => b.extend([p.lon, p.lat]))
        m.fitBounds(b, { padding: 40, maxZoom: 14, duration: 0 })
      }
    }
  }, [points, selectedId, line, ready])

  if (failed) {
    return (
      <div
        className={`flex items-center justify-center rounded border border-dashed border-zinc-300 bg-zinc-50 p-4 text-sm text-zinc-600 ${className ?? ''}`}
        role="status"
      >
        Map tiles are unavailable. Use the list instead.
      </div>
    )
  }
  return (
    <div
      ref={el}
      className={`rounded border border-zinc-200 ${className ?? ''}`}
      aria-label="Map of places"
    />
  )
}

function emptyFc(): GeoJSON.FeatureCollection {
  return { type: 'FeatureCollection', features: [] }
}
