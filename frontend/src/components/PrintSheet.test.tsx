import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import { PrintButton, PrintSheet } from './PrintSheet'

test('the sheet prints light with a file-friendly title, then restores both', () => {
  document.documentElement.dataset.theme = 'dark'
  document.title = 'TripWeaver'
  render(
    <PrintSheet title="3-day trip from Colva">
      <p>body</p>
    </PrintSheet>,
  )
  expect(screen.queryByRole('region', { name: 'Printable plan' })).not.toBeInTheDocument()
  act(() => {
    window.dispatchEvent(new Event('beforeprint'))
  })
  expect(screen.getByRole('region', { name: 'Printable plan' })).toHaveTextContent(
    '3-day trip from Colva',
  )
  expect(document.documentElement.dataset.theme).toBe('light')
  expect(document.title).toBe('TripWeaver – 3-day trip from Colva')
  act(() => {
    window.dispatchEvent(new Event('afterprint'))
  })
  expect(document.documentElement.dataset.theme).toBe('dark')
  expect(document.title).toBe('TripWeaver')
  expect(screen.queryByRole('region', { name: 'Printable plan' })).not.toBeInTheDocument()
})

test('Save as PDF opens the print dialog', async () => {
  const print = vi.spyOn(window, 'print').mockImplementation(() => {})
  render(<PrintButton />)
  await userEvent.click(screen.getByRole('button', { name: 'Save as PDF' }))
  expect(print).toHaveBeenCalledOnce()
})

test('a repeated beforeprint still restores the original theme and title', () => {
  document.documentElement.dataset.theme = 'dark'
  document.title = 'TripWeaver'
  render(
    <PrintSheet title="Road trip">
      <p>body</p>
    </PrintSheet>,
  )
  act(() => {
    window.dispatchEvent(new Event('beforeprint'))
    window.dispatchEvent(new Event('beforeprint'))
  })
  act(() => {
    window.dispatchEvent(new Event('afterprint'))
  })
  expect(document.documentElement.dataset.theme).toBe('dark')
  expect(document.title).toBe('TripWeaver')
})

test('while printing the body is marked so the page itself is hidden, without :has()', () => {
  render(
    <PrintSheet title="Road trip">
      <p>body</p>
    </PrintSheet>,
  )
  act(() => {
    window.dispatchEvent(new Event('beforeprint'))
  })
  expect(document.body).toHaveClass('printing-sheet')
  act(() => {
    window.dispatchEvent(new Event('afterprint'))
  })
  expect(document.body).not.toHaveClass('printing-sheet')
})

test('a title change during the print dialog keeps the sheet and the print title', () => {
  document.title = 'TripWeaver'
  const { rerender } = render(
    <PrintSheet title="Old name">
      <p>body</p>
    </PrintSheet>,
  )
  act(() => {
    window.dispatchEvent(new Event('beforeprint'))
  })
  rerender(
    <PrintSheet title="New name">
      <p>body</p>
    </PrintSheet>,
  )
  expect(screen.getByRole('region', { name: 'Printable plan' })).toBeInTheDocument()
  expect(document.documentElement.dataset.theme).toBe('light')
  act(() => {
    window.dispatchEvent(new Event('afterprint'))
  })
  expect(document.title).toBe('TripWeaver')
})
