import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import api, { errorMessage } from './api'

/**
 * Shared analysis scope (dataset, product, date range) stored in the URL so views are shareable,
 * with the last dataset remembered per browser.
 */
export function useScope() {
  const [params, setParams] = useSearchParams()
  const [datasets, setDatasets] = useState(null)
  const [products, setProducts] = useState([])
  const [error, setError] = useState('')

  const load = useCallback(() => {
    setError('')
    api.get('/datasets').then((r) => setDatasets(r.data.filter((d) => d.status === 'COMPLETED'))).catch((e) => setError(errorMessage(e)))
  }, [])
  useEffect(() => { load() }, [load])

  let stored = null
  try { stored = localStorage.getItem('rl_dataset') } catch { /* ignore */ }
  const requested = params.get('dataset')
  const datasetId = (datasets?.some((d) => d.id === requested) && requested)
    || (datasets?.some((d) => d.id === stored) && stored) || datasets?.[0]?.id || null
  const dataset = datasets?.find((d) => d.id === datasetId) || null

  useEffect(() => {
    if (!datasetId) return
    try { localStorage.setItem('rl_dataset', datasetId) } catch { /* ignore */ }
    api.get(`/datasets/${datasetId}/products`).then((r) => setProducts(r.data)).catch((e) => setError(errorMessage(e)))
  }, [datasetId])

  const update = useCallback((patch) => {
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)))
      return next
    }, { replace: true })
  }, [setParams])

  return {
    datasets, dataset, datasetId, products, error, reload: load, params, update,
    productId: params.get('product') || '',
    from: params.get('from') || '',
    to: params.get('to') || '',
    setDataset: (id) => { setProducts([]); update({ dataset: id, product: '', from: '', to: '' }) },
    setProduct: (id) => update({ product: id }),
    setRange: (from, to) => update({ from, to }),
  }
}

/** Calls fn after the value stops changing for `ms` milliseconds. */
export function useDebounced(value, ms = 400) {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}
