import { useState } from 'react'
import api, { errorMessage } from '../api'
import { Badge } from '../components/shared'

const EXAMPLES = [
  'The camera quality is excellent, but the battery drains quickly. The display is beautiful, although the phone is overpriced.',
  'Super fast performance with no lag at all. Sadly it heats up while gaming and the speakers sound tinny. Worth the price though.',
  'Feels cheap and plastic. Delivery arrived on time but customer support never answered my warranty request.',
]

export default function AnalyzerPage() {
  const [text, setText] = useState(EXAMPLES[0])
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const run = async () => {
    setBusy(true)
    setError('')
    try {
      setResult((await api.post('/analyze', { text })).data)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const r = result?.result
  return (
    <div className="stack">
      <div className="page-head">
        <div><h1>Live analyzer</h1><p>Type any product review to see aspect extraction and per-aspect sentiment in real time.</p></div>
      </div>
      {error && <div className="alert">{error}</div>}
      <div className="grid g2">
        <div className="card">
          <div className="card-head"><h2>Customer review</h2></div>
          <textarea rows={7} style={{ width: '100%' }} value={text} onChange={(e) => setText(e.target.value)} maxLength={10000} />
          <div className="row" style={{ marginTop: 12, justifyContent: 'space-between' }}>
            <div className="row" style={{ gap: 6 }}>
              <span className="muted small">Examples:</span>
              {EXAMPLES.map((e, i) => <button key={i} className="btn sm" onClick={() => { setText(e); setResult(null) }}>{i + 1}</button>)}
            </div>
            <button className="btn primary" onClick={run} disabled={busy || !text.trim()}>{busy ? 'Analysing…' : 'Analyse review'}</button>
          </div>
        </div>

        <div className="card">
          <div className="card-head">
            <h2>Aspect extraction & sentiment</h2>
            {r && <Badge value={r.overallSentiment}>Overall: {r.overallSentiment.toLowerCase()} ({r.overallScore > 0 ? '+' : ''}{r.overallScore.toFixed(2)})</Badge>}
          </div>
          {!r ? <div className="empty">Click <b>Analyse review</b> to run the NLP engine.</div> : (
            <>
              <table className="table">
                <thead><tr><th>Product aspect</th><th>Sentiment</th><th className="num">Score</th><th>Opinion</th></tr></thead>
                <tbody>
                  {r.aspects.map((a) => (
                    <tr key={a.aspect}>
                      <td style={{ fontWeight: 600 }}>{a.aspect}<div className="muted small">term: "{a.term}"</div></td>
                      <td><Badge value={a.sentiment} /></td>
                      <td className="num">{a.score > 0 ? '+' : ''}{a.score.toFixed(2)}</td>
                      <td className="muted">"{a.snippet}"</td>
                    </tr>
                  ))}
                  {r.aspects.length === 0 && <tr><td colSpan={4} className="muted">No product aspects detected.</td></tr>}
                </tbody>
              </table>
              <div className="insight" style={{ marginTop: 14 }}>
                <div><h3>Customer insight</h3><p>{result.insight}</p></div>
              </div>
            </>
          )}
        </div>
      </div>
      <div className="card">
        <h2 style={{ marginBottom: 8 }}>How it works</h2>
        <ol className="muted" style={{ margin: 0, paddingLeft: 18, lineHeight: 1.8 }}>
          <li><b>Pre-processing</b> - HTML, URLs and noise are removed; contractions are expanded so negations ("isn't") are kept.</li>
          <li><b>Clause segmentation</b> - the review is split into opinion units on sentence boundaries and contrast words (but, although, however…).</li>
          <li><b>Aspect extraction</b> - each clause is matched against an aspect lexicon (camera, battery, display, price, performance…) including synonyms such as "pics", "mAh", "screen".</li>
          <li><b>Aspect sentiment</b> - each clause is scored with VADER plus electronics-specific rules ("drains quickly" is negative for battery, "worth it" is positive for price).</li>
        </ol>
      </div>
    </div>
  )
}
