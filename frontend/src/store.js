import { reactive } from 'vue'
import { filters as fetchFilters } from './api'

export const store = reactive({
  options: null,
  filters: {
    orgId: '',
    city: '',
    ward: '',
    voltageLevel: '',
    manufacturerId: '',
    assetClass: '',
    status: '',
  },
})

export async function loadOptions(filters = store.filters, transformersOnly = false) {
  const params = toParams(filters)
  if (transformersOnly) params.set('transformersOnly', 'true')
  store.options = await fetchFilters(params)
}

export function toParams(f) {
  const p = new URLSearchParams()
  for (const k of ['orgId', 'city', 'ward', 'voltageLevel', 'manufacturerId', 'assetClass', 'status']) {
    if (f[k]) p.set(k, f[k])
  }
  return p
}
