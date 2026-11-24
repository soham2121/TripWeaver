import { useEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal, flushSync } from 'react-dom'

/**
 * A print-only copy of a plan, rendered next to #root only while printing (so the screen and its tests never see a
 * second copy). While printing, the body gets the class printing-sheet, which hides #root (index.css; a class, not
 * :has(), so older Firefox and Safari work too), so the PDF holds only the sheet. During a print the page switches to
 * the light theme and the document title becomes the default PDF file name; both come back afterwards, also when the
 * print dialog is cancelled or opened with Ctrl+P.
 */
export function PrintSheet({ title, children }: { title: string; children: ReactNode }) {
  const [printing, setPrinting] = useState(false)
  // Read at print time: a rename while the dialog is open must not tear the listeners down mid-print.
  const titleRef = useRef(title)
  useEffect(() => {
    titleRef.current = title
  })
  useEffect(() => {
    const root = document.documentElement
    let saved: { theme?: string; title: string } | null = null
    const before = () => {
      if (saved) return // some browsers fire beforeprint twice: keep the first saved state
      saved = { theme: root.dataset.theme, title: document.title }
      root.dataset.theme = 'light'
      document.title = `TripWeaver – ${titleRef.current}`
      document.body.classList.add('printing-sheet')
      flushSync(() => setPrinting(true)) // the print layout is taken right after this handler
    }
    const after = () => {
      if (!saved) return
      if (saved.theme === undefined) delete root.dataset.theme
      else root.dataset.theme = saved.theme
      document.title = saved.title
      document.body.classList.remove('printing-sheet')
      saved = null
      setPrinting(false)
    }
    window.addEventListener('beforeprint', before)
    window.addEventListener('afterprint', after)
    return () => {
      after() // unmounted mid-print: restore theme and title
      window.removeEventListener('beforeprint', before)
      window.removeEventListener('afterprint', after)
    }
  }, [])

  if (!printing) return null
  return createPortal(
    <section
      aria-label="Printable plan"
      className="print-sheet hidden bg-white text-sm text-zinc-900 print:block"
    >
      <h1 className="mb-1 text-xl font-bold">{title}</h1>
      <p className="mb-4 text-xs text-zinc-500">Made with TripWeaver</p>
      {children}
    </section>,
    document.body,
  )
}

/** Opens the browser's print dialog, where the user picks "Save as PDF". */
export function PrintButton() {
  return (
    <button type="button" className="btn-sm print:hidden" onClick={() => window.print()}>
      Save as PDF
    </button>
  )
}
