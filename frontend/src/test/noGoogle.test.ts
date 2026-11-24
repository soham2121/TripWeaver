import { expect, test } from 'vitest'

// Phase 11 (decision D39): no Google API in the frontend. The only Google artefact allowed is a plain
// https://www.google.com/maps/dir/?api=1 link built by the backend.
const FORBIDDEN =
  /googleapis\.com|maps\.google\.[a-z.]+\/maps\/api|@googlemaps|@react-google-maps|google\.maps\.|GOOGLE_[A-Z_]*KEY|VITE_GOOGLE/i

const sources = import.meta.glob(['../**/*.{ts,tsx}', '!../**/*.test.{ts,tsx}', '!../test/**'], {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

test('frontend sources call no Google API', () => {
  expect(Object.keys(sources).length).toBeGreaterThan(10)
  const hits = Object.entries(sources)
    .filter(([, code]) => FORBIDDEN.test(code))
    .map(([file]) => file)
  expect(hits).toEqual([])
})

const pkgRaw = Object.values(
  import.meta.glob('../../package.json', { query: '?raw', import: 'default', eager: true }),
)[0] as string

test('no Google client package', () => {
  const pkg = JSON.parse(pkgRaw) as {
    dependencies?: Record<string, string>
    devDependencies?: Record<string, string>
  }
  const deps = Object.keys({ ...pkg.dependencies, ...pkg.devDependencies })
  expect(deps.filter((d) => /google/i.test(d))).toEqual([])
})
