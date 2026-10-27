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

export async function loadOptions() {
  if (!store.options) {
    store.options = await fetchFilters()
  }
}

export function toParams(f) {
  const p = new URLSearchParams()
  for (const k of ['orgId', 'city', 'ward', 'voltageLevel', 'manufacturerId', 'assetClass', 'status']) {
    if (f[k]) p.set(k, f[k])
  }
  return p
}
