import { useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { CircleCheck, FileSpreadsheet, RotateCcw, Upload, X } from 'lucide-react'
import api, { errorMessage } from '../lib/api'
import { useStatus, useToast } from '../lib/providers'
import { fmtBytes } from '../lib/format'
import { Alert, Button, Field, Input, Select, cx } from './ui'

function extensionOf(name) {
  const lower = (name || '').toLowerCase()
  if (lower.endsWith('.jsonl.gz')) return 'jsonl.gz'
  const parts = lower.split('.')
  return parts.length > 1 ? parts.at(-1) : ''
}

/**
 * Drag-and-drop upload with server-side preview and upload progress.
 */
export function DatasetUploader({ onUploaded }) {
  const { limits } = useStatus()
  const toast = useToast()
  const input = useRef(null)
  const uploadController = useRef(null)

  const [file, setFile] = useState(null)
  const [name, setName] = useState('')
  const [preview, setPreview] = useState(null)
  const [mapping, setMapping] = useState({})
  const [over, setOver] = useState(false)
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState('')
  const [canRetry, setCanRetry] = useState(false)

  const formats = limits?.allowedFormats || ['csv', 'tsv', 'txt', 'jsonl', 'jsonl.gz', 'ndjson']
  const maxMb = limits?.maxUploadSizeMb || 5120

  const reset = () => {
    uploadController.current?.abort()
    uploadController.current = null
    setFile(null)
    setPreview(null)
    setMapping({})
    setName('')
    setError('')
    setProgress(0)
    setCanRetry(false)
    setBusy(false)
  }

  const pick = async (f) => {
    if (!f) return
    setError('')
    setCanRetry(false)
    const ext = extensionOf(f.name)
    if (!formats.includes(ext)) {
      setError(`Unsupported file type .${ext || 'unknown'}. Allowed: ${formats.join(', ')}`)
      return
    }
    if (f.size > maxMb * 1024 * 1024) {
      setError(`File is larger than the ${maxMb} MB upload limit.`)
      return
    }
    setFile(f)
    setName(f.name.replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' '))
    setBusy(true)
    try {
      const fd = new FormData()
      fd.append('file', new File([f.slice(0, 65536)], f.name, { type: 'application/octet-stream' }))
      const { data } = await api.post('/datasets/preview', fd)
      setPreview(data)
      setMapping(data.detectedMapping || {})
    } catch (e) {
      setError(errorMessage(e))
      setFile(null)
    } finally {
      setBusy(false)
    }
  }

  const upload = async () => {
    if (!file) return
    if (!mapping.text) {
      setError("Map a column to 'Review text' - it is required.")
      return
    }
    setBusy(true)
    setError('')
    setCanRetry(false)
    const controller = new AbortController()
    uploadController.current = controller
    try {
      const fd = new FormData()
      fd.append('file', file)
      fd.append('name', name)
      fd.append('mapping', new Blob([JSON.stringify(mapping)], { type: 'application/json' }))
      const { data } = await api.post('/datasets', fd, {
        signal: controller.signal,
        onUploadProgress: (e) => setProgress(Math.round((100 * e.loaded) / (e.total || 1))),
      })
      toast.success(`"${data.name}" uploaded - analysis has started.`)
      reset()
      onUploaded?.(data)
    } catch (e) {
      if (e?.code === 'ERR_CANCELED') {
        setError('Upload cancelled.')
      } else {
        setError(errorMessage(e))
        setCanRetry(true)
      }
    } finally {
      uploadController.current = null
      setBusy(false)
    }
  }

  const cancelUpload = () => {
    uploadController.current?.abort()
  }

  return (
    <div>
      <AnimatePresence mode="wait" initial={false}>
        {!preview ? (
          <motion.button key="drop" type="button" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={() => input.current?.click()} disabled={busy}
            onDragOver={(e) => { e.preventDefault(); setOver(true) }} onDragLeave={() => setOver(false)}
            onDrop={(e) => { e.preventDefault(); setOver(false); pick(e.dataTransfer.files[0]) }}
            className={cx('group flex w-full flex-col items-center justify-center rounded-2xl border-[1.5px] border-dashed px-6 py-10 text-center transition',
              over ? 'border-accent bg-accent-soft' : 'border-line-strong bg-subtle/60 hover:border-accent/60 hover:bg-accent-soft/40')}>
            <motion.span animate={over ? { y: -4, scale: 1.05 } : { y: 0, scale: 1 }}
              className="mb-4 grid size-12 place-items-center rounded-2xl bg-surface shadow-soft ring-1 ring-line">
              <Upload className="size-5 text-accent" />
            </motion.span>
            <span className="text-[15px] font-medium text-ink">{busy ? 'Reading file...' : 'Drop a review file here, or click to browse'}</span>
            <span className="mt-1.5 text-[13px] text-ink-3">{formats.map((f) => `.${f}`).join(', ')} - up to {maxMb} MB - needs a review text field</span>
          </motion.button>
        ) : (
          <motion.div key="preview" initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="space-y-5">
            <div className="flex flex-wrap items-center gap-3 rounded-xl border border-line bg-subtle/60 px-4 py-3">
              <FileSpreadsheet className="size-5 text-accent" />
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium">{file.name}</div>
                <div className="text-xs text-ink-3">{fmtBytes(file.size)} - {preview.columns.length} columns - format {preview.delimiter}</div>
              </div>
              <span className="flex items-center gap-1.5 text-xs text-success"><CircleCheck className="size-3.5" />Valid file</span>
              <button type="button" onClick={reset} className="rounded-md p-1 text-ink-3 hover:bg-muted hover:text-ink" aria-label="Remove file"><X className="size-4" /></button>
            </div>

            <div>
              <h4 className="mb-1 text-sm font-semibold">Field mapping</h4>
              <p className="mb-3 text-[13px] text-ink-3">Detected automatically from headers/keys. Adjust if needed.</p>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {preview.fields.map((f) => (
                  <Field key={f.key} label={<>{f.label}{f.required && <span className="text-danger"> *</span>}</>} hint={f.hint || undefined}>
                    <Select value={mapping[f.key] || ''} onChange={(e) => setMapping((m) => ({ ...m, [f.key]: e.target.value }))} aria-label={f.label}>
                      <option value="">{f.required ? 'Select a source field' : 'Not mapped'}</option>
                      {preview.columns.map((c) => <option key={c} value={c}>{c}</option>)}
                    </Select>
                  </Field>
                ))}
              </div>
            </div>

            <div className="flex flex-wrap items-end gap-3">
              <Field label="Dataset name" className="min-w-[220px] flex-1"><Input value={name} onChange={(e) => setName(e.target.value)} maxLength={150} /></Field>
              <Button onClick={reset} disabled={busy}>Cancel</Button>
              {busy && progress > 0 && <Button variant="ghost" onClick={cancelUpload}>Stop upload</Button>}
              {!busy && canRetry && <Button variant="ghost" icon={RotateCcw} onClick={upload}>Retry</Button>}
              <Button variant="primary" icon={Upload} loading={busy} onClick={upload}>{busy && progress ? `Uploading ${progress}%` : 'Upload and analyze'}</Button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
      <input ref={input} type="file" hidden accept={formats.map((f) => `.${f}`).join(',')} onChange={(e) => { pick(e.target.files[0]); e.target.value = '' }} />
      {error && <Alert className="mt-4">{error}</Alert>}
    </div>
  )
}
