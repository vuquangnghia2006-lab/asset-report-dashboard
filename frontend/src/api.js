const BASE = '/api/report'

function toParams(params) {
  if (params instanceof URLSearchParams) return params
  const p = new URLSearchParams()
  for (const k in params || {}) {
    const v = params[k]
    if (v !== undefined && v !== null && v !== '') p.set(k, v)
  }
  return p
}

async function get(path, params) {
  const q = toParams(params)
  const url = q.toString() ? BASE + path + '?' + q.toString() : BASE + path
  const res = await fetch(url)
  if (!res.ok) throw new Error(`API ${path} -> ${res.status}`)
  return res.json()
}

export function filters() {
  return get('/filters')
}

export function assetOverview(params) {
  return get('/asset-overview', params)
}

export function transformerFleet(params) {
  return get('/transformer-fleet', params)
}

export function parallelPairs(params, extra) {
  const p = toParams(params)
  for (const k in extra || {}) {
    const v = extra[k]
    if (v !== undefined && v !== null && v !== '') p.set(k, v)
  }
  return get('/transformer-fleet/parallel-pairs', p)
}

export function dataQuality(params) {
  return get('/data-quality', params)
}
