import { vi } from 'vitest'

type Handler = (url: URL, init?: RequestInit) => unknown | Response

/** Stubs global fetch. First route whose pattern matches the path (+query) wins. */
export function mockApi(routes: [RegExp, Handler][]) {
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://localhost')
    for (const [re, h] of routes) {
      if (re.test(url.pathname + url.search)) {
        const out = await h(url, init)
        if (out instanceof Response) return out
        return new Response(JSON.stringify(out), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }
    }
    return new Response(JSON.stringify({ errorCode: 'NOT_FOUND', message: 'no mock' }), {
      status: 404,
    })
  })
  vi.stubGlobal('fetch', fn)
  return fn
}
