import { render, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import { itineraryResponse } from '../test/planFixtures'
import { BudgetTable } from './BudgetTable'

test('an all-unknown budget never shows a ₹0 total', () => {
  const budget = {
    ...itineraryResponse().itinerary.budget,
    knownMin: 0,
    knownMax: 0,
    knownMaxPerPerson: 0,
  }
  render(<BudgetTable budget={budget} />)
  expect(screen.getByText(/No costs known yet for 2 people/)).toBeInTheDocument()
  expect(screen.queryByText(/Known total ₹0/)).not.toBeInTheDocument()
})

test('a per-person limit is shown as the group total with the per-person amount', () => {
  const budget = {
    ...itineraryResponse().itinerary.budget,
    partySize: 3,
    limit: 27000,
    limitBasis: 'PER_PERSON',
    strictness: 'HARD',
  }
  render(<BudgetTable budget={budget} />)
  expect(
    screen.getByText(/hard limit ₹27,000 for the group \(₹9,000 per person\)/),
  ).toBeInTheDocument()
})
