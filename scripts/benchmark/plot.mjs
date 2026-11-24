// Draws benchmark charts (static SVG, log-log) from raw CSVs in benchmark-results/raw.
// Usage (repo root): node scripts/benchmark/plot.mjs
// For each algorithm it uses the newest <alg>-YYYYMMDD-HHMMSS.csv and writes docs/benchmarks/<alg>.svg.
// Each line is the median time per operation across repetitions; the tables in docs/BENCHMARKS.md hold
// the same numbers with p25-p75.
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'

const RAW = 'benchmark-results/raw'
const OUT = 'docs/benchmarks'
// Validated categorical slots (light / dark), fixed order.
const LIGHT = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100']
const DARK = ['#3987e5', '#d95926', '#199e70', '#c98500']

const CHARTS = {
  trie: { title: 'Trie vs linear scan: time per autocomplete query', x: 'Indexed terms', unit: 'ns' },
  quickselect: { title: 'Randomized Quickselect vs sort-then-select (top 1%)', x: 'Candidates N', unit: 'ns' },
  setcover: { title: 'Weighted Set Cover: lazy heap vs full greedy', x: 'Candidates N', unit: 'ns' },
  heap: { title: 'Indexed max heap vs linear-scan list: time per operation', x: 'Operations N', unit: 'ns' },
  tsp: { title: 'Metric TSP: time per tour', x: 'Stops N', unit: 'ns' },
}

const newest = (alg) =>
  readdirSync(RAW)
    .filter((f) => new RegExp(`^${alg}-\\d{8}-\\d{6}\\.csv$`).test(f))
    .sort()
    .at(-1)

function medians(file) {
  const rows = readFileSync(`${RAW}/${file}`, 'utf8').trim().split(/\r?\n/).slice(1)
  const g = new Map()
  for (const r of rows) {
    const [, variant, size, , , , nsPerOp] = r.split(',')
    const k = variant
    if (!g.has(k)) g.set(k, new Map())
    const m = g.get(k)
    if (!m.has(+size)) m.set(+size, [])
    m.get(+size).push(+nsPerOp)
  }
  const series = []
  for (const [variant, bySize] of g) {
    const pts = [...bySize]
      .map(([n, v]) => {
        v.sort((a, b) => a - b)
        const mid = (v.length - 1) / 2
        return [n, (v[Math.floor(mid)] + v[Math.ceil(mid)]) / 2]
      })
      .sort((a, b) => a[0] - b[0])
    series.push({ variant, pts })
  }
  return series
}

const fmt = (v) =>
  v >= 1e6 ? `${+(v / 1e6).toPrecision(3)} ms` : v >= 1e3 ? `${+(v / 1e3).toPrecision(3)} µs` : `${+v.toPrecision(3)} ns`
const fmtN = (n) => (n >= 1e6 ? `${n / 1e6}M` : n >= 1e3 ? `${n / 1e3}K` : `${n}`)
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;')

function svg(alg, series) {
  const c = CHARTS[alg]
  if (series.length > 4) throw new Error(`${alg}: more than 4 series; fold or facet`)
  const W = 780, H = 400, L = 72, R = 230, T = 64, B = 52
  const xs = series.flatMap((s) => s.pts.map((p) => p[0]))
  const ys = series.flatMap((s) => s.pts.map((p) => p[1]))
  const lx = [Math.log10(Math.min(...xs)), Math.log10(Math.max(...xs))]
  const ly = [Math.floor(Math.log10(Math.min(...ys))), Math.ceil(Math.log10(Math.max(...ys)))]
  if (lx[0] === lx[1]) lx[1] += 1
  const X = (n) => L + ((Math.log10(n) - lx[0]) / (lx[1] - lx[0])) * (W - L - R)
  const Y = (v) => T + (1 - (Math.log10(v) - ly[0]) / (ly[1] - ly[0])) * (H - T - B)
  const out = []
  out.push(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" role="img" aria-label="${esc(c.title)}">`)
  out.push(`<style>
  .bg{fill:#fcfcfb}.t1{fill:#0b0b0b}.t2{fill:#52514e}.grid{stroke:#e4e3df}.axis{stroke:#b9b8b2}.ring{stroke:#fcfcfb}
  ${LIGHT.map((h, i) => `.s${i}{stroke:${h}}.f${i}{fill:${h}}`).join('')}
  @media (prefers-color-scheme: dark){.bg{fill:#1a1a19}.t1{fill:#f0efec}.t2{fill:#c3c2b7}.grid{stroke:#383835}.axis{stroke:#5d5c57}.ring{stroke:#1a1a19}
  ${DARK.map((h, i) => `.s${i}{stroke:${h}}.f${i}{fill:${h}}`).join('')}}
  text{font:12px system-ui,-apple-system,"Segoe UI",sans-serif}.title{font-size:15px;font-weight:600}
</style>`)
  out.push(`<rect class="bg" width="${W}" height="${H}"/>`)
  out.push(`<text class="t1 title" x="${L}" y="24">${esc(c.title)}</text>`)
  // Legend row (identity is never colour alone: lines also carry end labels).
  let lxPos = L
  series.forEach((s, i) => {
    out.push(`<line class="s${i}" x1="${lxPos}" y1="44" x2="${lxPos + 18}" y2="44" stroke-width="2"/>`)
    out.push(`<text class="t2" x="${lxPos + 24}" y="48">${esc(s.variant)}</text>`)
    lxPos += 34 + s.variant.length * 7
  })
  // Grid and y ticks at each decade.
  for (let e = ly[0]; e <= ly[1]; e++) {
    const y = Y(10 ** e)
    out.push(`<line class="grid" x1="${L}" x2="${W - R}" y1="${y}" y2="${y}" stroke-width="1"/>`)
    out.push(`<text class="t2" x="${L - 8}" y="${y + 4}" text-anchor="end">${fmt(10 ** e)}</text>`)
  }
  const sizes = [...new Set(xs)].sort((a, b) => a - b)
  for (const n of sizes)
    out.push(`<text class="t2" x="${X(n)}" y="${H - B + 18}" text-anchor="middle">${fmtN(n)}</text>`)
  out.push(`<line class="axis" x1="${L}" x2="${W - R}" y1="${H - B}" y2="${H - B}" stroke-width="1"/>`)
  out.push(`<text class="t2" x="${(L + W - R) / 2}" y="${H - 10}" text-anchor="middle">${esc(c.x)} (log scale)</text>`)
  out.push(`<text class="t2" transform="translate(16 ${(T + H - B) / 2}) rotate(-90)" text-anchor="middle">median time per operation (log scale)</text>`)
  // Lines, markers with a surface ring, and end labels pushed apart to avoid collisions.
  const ends = []
  series.forEach((s, i) => {
    const d = s.pts.map((p, j) => `${j ? 'L' : 'M'}${X(p[0]).toFixed(1)},${Y(p[1]).toFixed(1)}`).join('')
    out.push(`<path class="s${i}" d="${d}" fill="none" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>`)
    for (const [n, v] of s.pts)
      out.push(`<circle class="f${i} ring" cx="${X(n).toFixed(1)}" cy="${Y(v).toFixed(1)}" r="4" stroke-width="2"><title>${esc(s.variant)}, N=${fmtN(n)}: ${fmt(v)}</title></circle>`)
    const last = s.pts.at(-1)
    ends.push({ i, y: Y(last[1]), x: X(last[0]), label: `${s.variant} ${fmt(last[1])}` })
  })
  ends.sort((a, b) => a.y - b.y)
  for (let k = 1; k < ends.length; k++) if (ends[k].y - ends[k - 1].y < 14) ends[k].y = ends[k - 1].y + 14
  for (const e of ends) out.push(`<text class="t1" x="${e.x + 10}" y="${e.y + 4}">${esc(e.label)}</text>`)
  out.push('</svg>')
  return out.join('\n')
}

mkdirSync(OUT, { recursive: true })
for (const alg of Object.keys(CHARTS)) {
  const f = newest(alg)
  if (!f) {
    console.log(`${alg}: no raw CSV, skipped`)
    continue
  }
  let series = medians(f)
  // Set Cover: keep the four lazy/full x U series; TSP: drop Held-Karp (two sizes only) to stay at 4 series.
  if (alg === 'tsp') series = series.filter((s) => s.variant !== 'exact-held-karp')
  writeFileSync(`${OUT}/${alg}.svg`, svg(alg, series))
  console.log(`${alg}: ${f} -> ${OUT}/${alg}.svg (${series.map((s) => s.variant).join(', ')})`)
}
