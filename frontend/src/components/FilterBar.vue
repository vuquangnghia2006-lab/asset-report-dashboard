<script setup>
import { reactive, onMounted } from 'vue'
import { store, loadOptions } from '../store'

const props = defineProps({
  fields: { type: Array, default: () => ['orgId', 'city', 'ward', 'voltageLevel', 'manufacturerId', 'assetClass', 'status'] },
})

const emit = defineEmits(['apply'])

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

function syncFromStore() {
  for (const k in editable) editable[k] = store.filters[k]
}

function apply() {
  store.filters = { ...store.filters, ...editable }
  emit('apply', { ...store.filters })
}

function reset() {
  for (const k in editable) editable[k] = ''
  store.filters = { ...store.filters, ...editable }
  emit('apply', { ...store.filters })
}

onMounted(async () => {
  await loadOptions()
  syncFromStore()
})

const options = (key) => (store.options ? store.options[key] : [])
</script>

<template>
  <div class="filterbar">
    <div v-for="k in props.fields" :key="k">
      <label>{{ FIELDS[k].label }}</label>
      <select v-model="editable[k]">
        <option value="">All</option>
        <option v-for="o in options(FIELDS[k].key)" :key="o.value" :value="o.value">
          {{ o.label }} ({{ o.count }})
        </option>
      </select>
    </div>
    <div class="filter-actions">
      <button class="btn" @click="apply">Apply</button>
      <button class="btn secondary" @click="reset">Reset</button>
    </div>
  </div>
</template>
