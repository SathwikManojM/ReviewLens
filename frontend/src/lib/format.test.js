import { describe, expect, it } from 'vitest'
import { fmtDuration, fmtScore, pct, periodLabel, segmentText } from './format'

describe('format helpers', () => {
  it('computes rounded percentages safely', () => {
    expect(pct(1, 3)).toBe(33)
    expect(pct(5, 0)).toBe(0)
  })

  it('formats signed sentiment scores', () => {
    expect(fmtScore(0.5)).toBe('+0.50')
    expect(fmtScore(-0.25)).toBe('−0.25')
    expect(fmtScore(null)).toBe('–')
  })

  it('formats durations', () => {
    expect(fmtDuration(850)).toBe('850 ms')
    expect(fmtDuration(1500)).toBe('1.5 s')
  })

  it('labels ISO weeks', () => {
    expect(periodLabel('2026-W07')).toBe('W07')
  })
})

describe('segmentText', () => {
  const text = 'The camera quality is excellent, but the battery drains quickly.'

  it('highlights each aspect opinion once, in order', () => {
    const parts = segmentText(text, [
      { aspect: 'Battery', sentiment: 'NEGATIVE', snippet: 'the battery drains quickly' },
      { aspect: 'Camera', sentiment: 'POSITIVE', snippet: 'The camera quality is excellent' },
    ])
    expect(parts.map((p) => p.sentiment ?? null)).toEqual(['POSITIVE', null, 'NEGATIVE', null])
    expect(parts.map((p) => p.text).join('')).toBe(text)
  })

  it('ignores snippets that are not in the text', () => {
    expect(segmentText('Great phone', [{ snippet: 'battery', sentiment: 'NEGATIVE' }])).toEqual([{ text: 'Great phone' }])
  })
})
