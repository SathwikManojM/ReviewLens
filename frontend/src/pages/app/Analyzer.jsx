import { useCallback, useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Info, Sparkles, Zap } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { useDebounced } from '../../lib/scope'
import { useStatus } from '../../lib/providers'
import { fmtScore } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { AspectIcon, HighlightedText, InsightCard } from '../../components/data'
import { Button, Card, CardHeader, EmptyState, ErrorState, SentimentBadge, Skeleton, Switch, Textarea } from '../../components/ui'
import { usePalette } from '../../components/charts'

const EXAMPLES = [
  'The camera quality is excellent, but the battery drains quickly. The display is beautiful, although the phone is overpriced.',
  'Super fast performance with no lag at all. Sadly it heats up while gaming and the speakers sound tinny. Worth the price though.',
  'Feels cheap and plastic. Delivery arrived on time but customer support never answered my warranty request.',
]

export default function Analyzer() {
  const { feature } = useStatus()
  const c = usePalette()
  const [text, setText] = useState(EXAMPLES[0])
  const [auto, setAuto] = useState(true)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const seq = useRef(0)
  const debounced = useDebounced(text, 700)

  const run = useCallback(async (value) => {
    if (!value.trim()) { setResult(null); return }
    const id = ++seq.current
    setBusy(true)
    setError('')
    try {
      const r = await api.post('/analyze', { text: value })
      if (id === seq.current) setResult(r.data)
    } catch (e) {
      if (id === seq.current) setError(errorMessage(e))
    } finally {
      if (id === seq.current) setBusy(false)
    }
  }, [])
  useEffect(() => { if (auto) run(debounced) }, [debounced, auto, run])

  if (!feature('liveAnalyzer')) return <><PageHeader title="Live analyzer" /><Card><EmptyState title="The live analyzer is disabled">An administrator has turned this feature off.</EmptyState></Card></>
  const r = result?.result

  return (
    <>
      <PageHeader eyebrow="Workspace" title="Live analyzer" description="Paste any product review and watch the NLP engine extract aspects and score each one as you type." />
      <div className="grid gap-5 lg:grid-cols-[1fr_1.1fr]">
        <Card className="flex flex-col">
          <CardHeader title="Customer review" description="Up to 10,000 characters" action={<span className="tnum text-xs text-ink-3">{text.length.toLocaleString()} chars</span>} />
          <div className="flex flex-1 flex-col gap-4 p-5">
            <Textarea rows={9} value={text} onChange={(e) => setText(e.target.value)} maxLength={10000} className="flex-1 text-[15px]" aria-label="Review text" />
            <div className="flex flex-wrap gap-2">
              {EXAMPLES.map((e, i) => <Button key={i} size="xs" variant="subtle" onClick={() => setText(e)}>Example {i + 1}</Button>)}
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4">
              <div className="w-56"><Switch checked={auto} onChange={setAuto} label="Analyse as I type" /></div>
              <Button variant="primary" icon={Zap} loading={busy} disabled={!text.trim()} onClick={() => run(text)}>Analyse now</Button>
            </div>
          </div>
        </Card>

        <Card>
          <CardHeader title="Extracted aspects" description="Each opinion is scored separately"
            action={r && <SentimentBadge value={r.overallSentiment}>Overall {r.overallSentiment.toLowerCase()} · {fmtScore(r.overallScore)}</SentimentBadge>} />
          <div className="space-y-5 p-5">
            {error ? <ErrorState message={error} onRetry={() => run(text)} /> : !r ? (
              busy ? <div className="space-y-2">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-16" />)}</div>
                : <EmptyState icon={Sparkles} title="Start typing a review">Mention features like the camera, battery or price.</EmptyState>
            ) : (
              <div className={busy ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
                <div className="rounded-xl bg-subtle/70 p-4"><HighlightedText text={text} aspects={r.aspects} className="text-[14.5px]" /></div>
                <ul className="mt-4 space-y-2">
                  <AnimatePresence initial={false}>
                    {r.aspects.map((a) => (
                      <motion.li key={a.aspect} layout initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
                        className="flex items-center gap-3 rounded-xl border border-line px-4 py-3">
                        <span className="grid size-9 place-items-center rounded-lg bg-subtle"><AspectIcon aspect={a.aspect} className="size-4 text-ink-2" /></span>
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2"><span className="text-sm font-semibold">{a.aspect}</span><span className="text-xs text-ink-3">term “{a.term}”</span></div>
                          <p className="truncate text-[13px] text-ink-2">“{a.snippet}”</p>
                        </div>
                        <div className="w-24 text-right">
                          <SentimentBadge value={a.sentiment} />
                          <div className="mt-1.5 h-1 overflow-hidden rounded-full bg-subtle">
                            <div className="h-full rounded-full" style={{ width: `${Math.round(Math.abs(a.score) * 100)}%`, background: a.score >= 0 ? c.pos : c.neg }} />
                          </div>
                          <div className="tnum mt-0.5 text-[11px] text-ink-3">{fmtScore(a.score)}</div>
                        </div>
                      </motion.li>
                    ))}
                  </AnimatePresence>
                  {r.aspects.length === 0 && <li className="text-sm text-ink-3">No product aspects detected in this text.</li>}
                </ul>
              </div>
            )}
            {r && <InsightCard text={result.insight} />}
            <p className="flex items-start gap-2 text-xs leading-relaxed text-ink-3">
              <Info className="mt-0.5 size-3.5 shrink-0" />Engine: {result?.engine || 'rule-based-vader'} — a transparent rule-based baseline (clause segmentation, aspect lexicon, VADER with electronics rules). Results are not produced by a transformer model.
            </p>
          </div>
        </Card>
      </div>
    </>
  )
}
