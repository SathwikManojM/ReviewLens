import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ArrowRight, ChartColumn, Database, FileText, GitCompareArrows, Layers, MessageSquareText, ScanSearch, Sparkles, Workflow, Zap } from 'lucide-react'
import api, { errorMessage } from '../lib/api'
import { useAuth } from '../lib/providers'
import { ThemeToggle, Wordmark } from '../components/layout'
import { AspectIcon } from '../components/data'
import { Button, SentimentBadge, Skeleton, Textarea } from '../components/ui'

const EXAMPLE = 'The camera quality is excellent, but the battery drains quickly. The display is beautiful, although the phone is overpriced.'

const FEATURES = [
  { icon: ScanSearch, title: 'Aspect extraction', text: 'Finds the product features customers talk about — camera, battery, display, price, performance, build quality and more.' },
  { icon: Sparkles, title: 'Aspect-level sentiment', text: 'Scores the opinion about each feature separately, so “great camera, weak battery” is never averaged away.' },
  { icon: MessageSquareText, title: 'Keywords & concerns', text: 'Surfaces what customers praise and the pain points that keep coming up across thousands of reviews.' },
  { icon: GitCompareArrows, title: 'Product comparison', text: 'Puts two to four products side by side, aspect by aspect, with clear strengths and weaknesses.' },
  { icon: ChartColumn, title: 'Analytics dashboard', text: 'Sentiment distribution, trends over time, ratings and a generated customer-insight summary.' },
  { icon: FileText, title: 'Reports', text: 'Export a polished PDF report or the full aspect-level data as CSV for your own analysis.' },
]

const STEPS = [
  { n: '01', title: 'Upload reviews', text: 'Drop a public Amazon review CSV. Columns are detected automatically and can be adjusted.' },
  { n: '02', title: 'Clean & segment', text: 'Text is normalised and split into opinion clauses on words like “but” and “although”.' },
  { n: '03', title: 'Extract & score', text: 'Each clause is matched to product aspects and scored with VADER plus electronics rules.' },
  { n: '04', title: 'Explore insight', text: 'Dashboards, comparisons, the review explorer and reports — all from the stored results.' },
]

function LiveDemo() {
  const [text, setText] = useState(EXAMPLE)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const run = async (t = text) => {
    setBusy(true)
    setError('')
    try { setResult((await api.post('/public/demo-analyze', { text: t.slice(0, 600) })).data) } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) }
  }
  useEffect(() => { run(EXAMPLE) }, []) // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <div className="card overflow-hidden text-left shadow-pop">
      <div className="flex items-center gap-2 border-b border-line bg-subtle/70 px-4 py-3">
        <span className="size-3 rounded-full bg-[#ff5f57]" /><span className="size-3 rounded-full bg-[#febc2e]" /><span className="size-3 rounded-full bg-[#28c840]" />
        <span className="ml-3 text-xs font-medium text-ink-3">Live analyzer · runs the real NLP engine</span>
      </div>
      <div className="grid gap-0 md:grid-cols-[1.05fr_1fr]">
        <div className="border-b border-line p-5 md:border-b-0 md:border-r">
          <label htmlFor="demo" className="eyebrow">Customer review</label>
          <Textarea id="demo" rows={6} value={text} maxLength={600} onChange={(e) => setText(e.target.value)} className="mt-2 text-[15px]" />
          <div className="mt-3 flex items-center justify-between">
            <span className="tnum text-xs text-ink-3">{text.length}/600</span>
            <Button variant="primary" size="sm" icon={Zap} loading={busy} onClick={() => run()} disabled={!text.trim()}>Analyse</Button>
          </div>
        </div>
        <div className="p-5">
          <div className="eyebrow">Aspects & sentiment</div>
          <div className="mt-3 space-y-2">
            {busy && !result && [0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-12" />)}
            {error && <p className="rounded-lg bg-subtle px-3 py-4 text-sm text-ink-2">{error}</p>}
            {result?.result?.aspects?.map((a, i) => (
              <motion.div key={a.aspect} initial={{ opacity: 0, x: 8 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.07 }}
                className="flex items-center gap-3 rounded-xl border border-line px-3 py-2.5">
                <span className="grid size-8 place-items-center rounded-lg bg-subtle"><AspectIcon aspect={a.aspect} className="size-4 text-ink-2" /></span>
                <div className="min-w-0 flex-1"><div className="text-sm font-medium">{a.aspect}</div><div className="truncate text-xs text-ink-3">“{a.snippet}”</div></div>
                <SentimentBadge value={a.sentiment} />
              </motion.div>
            ))}
            {result && result.result.aspects.length === 0 && <p className="text-sm text-ink-3">No product aspects detected — try mentioning the camera, battery or price.</p>}
          </div>
          {result?.insight && <p className="mt-4 border-t border-line pt-3 text-[13px] leading-relaxed text-ink-2">{result.insight}</p>}
        </div>
      </div>
    </div>
  )
}

function Illustration() {
  const rows = [[22, 70], [58, 34], [14, 80], [40, 52], [30, 61]]
  return (
    <div className="card p-6" aria-hidden="true">
      <div className="mb-4 flex items-center justify-between"><span className="text-sm font-semibold">Sentiment by aspect</span><span className="text-[11px] text-ink-3">Illustration</span></div>
      <div className="space-y-3">
        {rows.map(([n, p], i) => (
          <div key={i} className="grid grid-cols-[70px_1fr_1fr] items-center gap-3">
            <Skeleton className="h-2.5 animate-none" />
            <div className="flex justify-end"><motion.div className="h-3.5 rounded-l-md bg-neg" initial={{ width: 0 }} whileInView={{ width: `${n}%` }} viewport={{ once: true }} transition={{ duration: 0.8, delay: i * 0.08 }} /></div>
            <div className="border-l-2 border-ink/15"><motion.div className="h-3.5 rounded-r-md bg-pos" initial={{ width: 0 }} whileInView={{ width: `${p}%` }} viewport={{ once: true }} transition={{ duration: 0.8, delay: i * 0.08 }} /></div>
          </div>
        ))}
      </div>
    </div>
  )
}

const reveal = { initial: { opacity: 0, y: 18 }, whileInView: { opacity: 1, y: 0 }, viewport: { once: true, margin: '-60px' }, transition: { duration: 0.55, ease: [0.22, 1, 0.36, 1] } }

export default function Landing() {
  const { user } = useAuth()
  return (
    <div className="min-h-screen bg-canvas">
      <header className="sticky top-0 z-30 border-b border-line/70 bg-canvas/75 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-6 px-5">
          <Wordmark />
          <nav className="hidden items-center gap-6 text-[13.5px] text-ink-2 md:flex">
            <a href="#features" className="hover:text-ink">Features</a><a href="#workflow" className="hover:text-ink">How it works</a><a href="#analytics" className="hover:text-ink">Analytics</a>
          </nav>
          <div className="ml-auto flex items-center gap-1.5">
            <ThemeToggle />
            {user ? <Link to="/app/dashboard"><Button variant="invert" size="sm" iconRight={ArrowRight}>Open workspace</Button></Link> : <>
              <Link to="/login"><Button variant="ghost" size="sm">Sign in</Button></Link>
              <Link to="/register"><Button variant="invert" size="sm">Get started</Button></Link>
            </>}
          </div>
        </div>
      </header>

      <section className="relative overflow-hidden">
        <div className="pointer-events-none absolute inset-x-0 top-0 h-[520px] bg-[radial-gradient(60%_60%_at_50%_0%,color-mix(in_srgb,var(--accent)_12%,transparent),transparent)]" />
        <div className="relative mx-auto max-w-6xl px-5 pb-20 pt-16 text-center sm:pt-24">
          <motion.div {...reveal} className="mx-auto mb-6 inline-flex items-center gap-2 rounded-full border border-line bg-surface px-3 py-1 text-xs text-ink-2 shadow-soft">
            <span className="size-1.5 rounded-full bg-accent" />Aspect-based sentiment analysis for product reviews
          </motion.div>
          <motion.h1 {...reveal} className="mx-auto max-w-4xl text-[42px] font-semibold leading-[1.04] tracking-[-0.04em] sm:text-[68px]">
            Every review, understood <span className="text-ink-3">feature by feature.</span>
          </motion.h1>
          <motion.p {...reveal} transition={{ ...reveal.transition, delay: 0.08 }} className="mx-auto mt-6 max-w-2xl text-[17px] leading-relaxed text-ink-2 sm:text-lg">
            ReviewLens reads thousands of Amazon reviews and tells you exactly how customers feel about the camera, the battery, the display and the price — not just whether a review was positive.
          </motion.p>
          <motion.div {...reveal} transition={{ ...reveal.transition, delay: 0.14 }} className="mt-9 flex flex-wrap justify-center gap-3">
            <Link to={user ? '/app/dashboard' : '/register'}><Button variant="primary" size="lg" iconRight={ArrowRight}>{user ? 'Open workspace' : 'Start analysing'}</Button></Link>
            <a href="#workflow"><Button size="lg">How it works</Button></a>
          </motion.div>
          <motion.div initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, delay: 0.25, ease: [0.22, 1, 0.36, 1] }} className="mx-auto mt-16 max-w-4xl">
            <LiveDemo />
          </motion.div>
        </div>
      </section>

      <section id="features" className="mx-auto max-w-6xl px-5 py-20">
        <motion.div {...reveal} className="max-w-2xl">
          <div className="eyebrow mb-3">Capabilities</div>
          <h2 className="text-[34px] font-semibold leading-tight tracking-[-0.03em] sm:text-[42px]">The voice of your customer, organised.</h2>
        </motion.div>
        <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((f, i) => (
            <motion.div key={f.title} {...reveal} transition={{ ...reveal.transition, delay: i * 0.05 }} className="card group p-6 transition hover:-translate-y-0.5 hover:shadow-pop">
              <span className="grid size-10 place-items-center rounded-xl bg-accent-soft text-accent transition group-hover:scale-105"><f.icon className="size-5" /></span>
              <h3 className="mt-5 text-[16px] font-semibold tracking-tight">{f.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-ink-2">{f.text}</p>
            </motion.div>
          ))}
        </div>
      </section>

      <section id="workflow" className="border-y border-line bg-surface/60 py-20">
        <div className="mx-auto max-w-6xl px-5">
          <motion.div {...reveal} className="max-w-2xl">
            <div className="eyebrow mb-3 flex items-center gap-2"><Workflow className="size-3.5" />How it works</div>
            <h2 className="text-[34px] font-semibold leading-tight tracking-[-0.03em] sm:text-[42px]">From raw CSV to clear insight in four steps.</h2>
          </motion.div>
          <div className="mt-12 grid gap-6 md:grid-cols-4">
            {STEPS.map((s, i) => (
              <motion.div key={s.n} {...reveal} transition={{ ...reveal.transition, delay: i * 0.08 }} className="relative">
                <div className="tnum text-[13px] font-semibold text-accent">{s.n}</div>
                <div className="hairline my-4" />
                <h3 className="text-[16px] font-semibold tracking-tight">{s.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-ink-2">{s.text}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      <section id="analytics" className="mx-auto grid max-w-6xl items-center gap-12 px-5 py-24 lg:grid-cols-2">
        <motion.div {...reveal}>
          <div className="eyebrow mb-3">Analytics</div>
          <h2 className="text-[34px] font-semibold leading-tight tracking-[-0.03em] sm:text-[42px]">See what customers love — and what they don’t.</h2>
          <ul className="mt-8 space-y-4 text-[15px] text-ink-2">
            {['Diverging sentiment bars for every product aspect', 'Monthly trends for review volume and aspect sentiment', 'Side-by-side comparison of up to four products', 'Every figure traces back to the reviews behind it'].map((t) => (
              <li key={t} className="flex gap-3"><span className="mt-2 size-1.5 shrink-0 rounded-full bg-accent" />{t}</li>
            ))}
          </ul>
        </motion.div>
        <motion.div {...reveal}><Illustration /></motion.div>
      </section>

      <section className="mx-auto max-w-6xl px-5 pb-24">
        <motion.div {...reveal} className="card flex flex-col items-center gap-6 px-6 py-14 text-center">
          <div className="flex flex-wrap justify-center gap-2 text-xs text-ink-3">
            {[[Layers, 'React + Vite'], [Zap, 'Spring Boot'], [Database, 'MongoDB'], [Sparkles, 'Python FastAPI · VADER baseline']].map(([I, t]) => (
              <span key={t} className="inline-flex items-center gap-1.5 rounded-full border border-line px-3 py-1"><I className="size-3.5" />{t}</span>
            ))}
          </div>
          <h2 className="max-w-2xl text-[30px] font-semibold leading-tight tracking-[-0.03em] sm:text-[38px]">Turn your next review dataset into decisions.</h2>
          <Link to={user ? '/app/datasets' : '/register'}><Button variant="primary" size="lg" iconRight={ArrowRight}>{user ? 'Upload a dataset' : 'Create a free account'}</Button></Link>
        </motion.div>
      </section>

      <footer className="border-t border-line py-8">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-5 text-xs text-ink-3">
          <span>© {new Date().getFullYear()} ReviewLens · DSCI-12 capstone</span>
          <span>Uses publicly available review datasets — no scraping.</span>
        </div>
      </footer>
    </div>
  )
}
