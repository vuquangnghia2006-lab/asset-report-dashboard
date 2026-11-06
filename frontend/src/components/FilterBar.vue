<script setup>
import { reactive, onMounted } from 'vue'
import { store, loadOptions } from '../store'

const props = defineProps({
  fields: { type: Array, default: () => ['orgId', 'voltageLevel', 'manufacturerId', 'assetClass', 'status'] },
  transformersOnly: { type: Boolean, default: false },
})

const FIELDS = {
  orgId: { label: 'Organisation', key: 'organisations' },
  city: { label: 'City / Province', key: 'cities' },
  ward: { label: 'Ward / Commune', key: 'wards' },
  voltageLevel: { label: 'Voltage level', key: 'voltageLevels' },
  manufacturerId: { label: 'Manufacturer', key: 'manufacturers' },
  assetClass: { label: 'Asset class', key: 'assetClasses' },
  status: { label: 'Status', key: 'statuses' },
}

const editable = reactive({ ...store.filters })

async function update(key) {
  store.filters = { ...store.filters, ...editable }
  await loadOptions(store.filters, props.transformersOnly)
  let cleared = false
  for (const field of props.fields) {
    const value = editable[field]
    if (value && !options(FIELDS[field].key).some((option) => option.value === value)) {
      editable[field] = ''
      store.filters = { ...store.filters, [field]: '' }
      cleared = true
    }
  }
  if (cleared) await loadOptions(store.filters, props.transformersOnly)
}

async function reset() {
  for (const k in editable) editable[k] = ''
  store.filters = { ...store.filters, ...editable }
  await loadOptions(store.filters, props.transformersOnly)
}

onMounted(async () => {
  await loadOptions(store.filters, props.transformersOnly)
})

const options = (key) => (store.options ? store.options[key] : [])
</script>

<template>
  <div class="filterbar">
    <div v-for="k in props.fields" :key="k">
      <label>{{ FIELDS[k].label }}</label>
      <select v-model="editable[k]" @change="update(k)">
        <option value="">All</option>
        <option v-for="o in options(FIELDS[k].key)" :key="o.value" :value="o.value">
          {{ o.label }} ({{ o.count }})
        </option>
      </select>
    </div>
    <div class="filter-actions">
      <button class="btn secondary" @click="reset">Reset</button>
    </div>
  </div>
</template>
