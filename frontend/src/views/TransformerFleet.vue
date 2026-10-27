<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { transformerFleet, parallelPairs } from '../api'
import { store, toParams } from '../store'
import FilterBar from '../components/FilterBar.vue'
import EChart from '../components/EChart.vue'
import { fmt, pct } from '../format'
import { exportCSV, exportExcel, exportPDF } from '../export'
import { registerExporter } from '../exportStore'

const GOOD = '#16a34a'
const WARN = '#f59e0b'
const BAD = '#ef4444'
const BLUE = '#2563eb'
const VOLT_COLORS = ['#2563eb', '#16a34a', '#f59e0b', '#8b5cf6', '#0ea5e9', '#ef4444']
const ORG_COLORS = ['#2563eb', '#16a34a', '#f59e0b', '#8b5cf6', '#ef4444', '#0ea5e9', '#14b8a6', '#f43f5e']

const fleet = ref(null)
const pairs = ref(null)
const loading = ref(false)
const loadingPairs = ref(false)
const error = ref(null)
const errorPairs = ref(null)
const router = useRouter()

function goHome() {
  store.filters = { orgId: '', city: '', ward: '', voltageLevel: '', manufacturerId: '', assetClass: '', status: '' }
  router.push('/')
}

const parCfg = reactive({
  maxUkDiff: 10,
  sameSite: true,
  requireTapOverlap: true,
  maxPowerRatio: 0,
})

async function fetchFleet() {
  loading.value = true
  error.value = null
  try {
    fleet.value = await transformerFleet(toParams(store.filters))
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function fetchPairs() {
  loadingPairs.value = true
  errorPairs.value = null
  try {
    pairs.value = await parallelPairs(
      {
        orgId: store.filters.orgId || undefined,
        city: store.filters.city || undefined,
        ward: store.filters.ward || undefined,
        voltageLevel: store.filters.voltageLevel || undefined,
        manufacturerId: store.filters.manufacturerId || undefined,
        status: store.filters.status || undefined,
      },
      parCfg,
    )
  } catch (e) {
    errorPairs.value = e.message
  } finally {
    loadingPairs.value = false
  }
}

watch(() => store.filters, () => {
  fetchFleet()
  fetchPairs()
})
onMounted(() => {
  fetchFleet()
  fetchPairs()
})

const activeCriterion = ref('')
const criteria = computed(() => {
  const all = pairs.value?.pairs || []
  return [
    { key: 'ratio', label: 'Transformation ratio', desc: 'Exact match or deviation within configured tolerance.',
      count: all.filter((p) => p.ratioOk === false).length },
    { key: 'vector', label: 'Vector group', desc: 'Must be compatible in phase displacement.',
      count: all.filter((p) => p.vectorOk === false).length },
    { key: 'uk', label: 'Impedance Uk%', desc: 'Relative deviation compared against technical threshold.',
      count: all.filter((p) => p.ukOk === false).length },
    { key: 'tap', label: 'Tap & rated power', desc: 'Checks tap range and rated power ratio where data is available.',
      count: all.filter((p) => p.tapOk === false).length },
  ]
})

const visiblePairs = computed(() => {
  const all = pairs.value?.pairs || []
  if (!activeCriterion.value) return all
  return all.filter((p) => {
    const field = activeCriterion.value === 'ratio' ? 'ratioOk'
      : activeCriterion.value === 'vector' ? 'vectorOk'
      : activeCriterion.value === 'uk' ? 'ukOk' : 'tapOk'
    return p[field] === false
  })
})

const gridBase = {
  grid: { left: 48, right: 16, top: 30, bottom: 30 },
}

const capacityOption = computed(() => {
  const data = fleet.value?.capacityByVoltage || []
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(37,99,235,0.06)' } },
    },
    ...gridBase,
    xAxis: {
      type: 'category',
      data: data.map((d) => d.label),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dbe3ea' } },
      axisLabel: { fontSize: 11, color: '#64748b' },
    },
    yAxis: {
      type: 'value',
      name: 'MVA',
      nameTextStyle: { color: '#94a3b8', fontSize: 11 },
      axisLabel: { fontSize: 11, color: '#64748b' },
      splitLine: { lineStyle: { color: '#eef2f6' } },
    },
    series: [
      {
        type: 'bar',
        data: data.map((d, i) => ({
          value: d.value,
          itemStyle: { color: VOLT_COLORS[i % VOLT_COLORS.length], borderRadius: [6, 6, 0, 0] },
        })),
        label: {
          show: true,
          position: 'top',
          fontSize: 11,
          fontWeight: 700,
          color: '#334155',
          formatter: (p) => fmt(p.value),
        },
        barMaxWidth: 52,
      },
    ],
  }
})

const mixTotal = computed(() =>
  (fleet.value?.topManufacturers || []).reduce((s, d) => s + (Number(d.count) || 0), 0),
)

const mixOption = computed(() => {
  const data = fleet.value?.topManufacturers || []
  const total = mixTotal.value || 0
  return {
    tooltip: {
      trigger: 'item',
      formatter: (p) => {
        const share = total ? Math.round((1000 * p.value) / total) / 10 : 0
        return `${p.name}<br/>${fmt(p.value)} &middot; ${share}%`
      },
    },
    title: {
      text: fmt(total),
      subtext: 'units',
      left: 'center',
      top: '40%',
      textStyle: { fontSize: 24, fontWeight: 800, color: '#0f2a5c' },
      subtextStyle: { fontSize: 11, color: '#94a3b8' },
      itemGap: 2,
    },
    legend: {
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      textStyle: { fontSize: 11, color: '#475569' },
      formatter: (name) => {
        const item = data.find((d) => d.name === name)
        const share = total && item ? Math.round((1000 * item.count) / total) / 10 : 0
        return `${name} — ${share}%`
      },
    },
    series: [
      {
        type: 'pie',
        radius: ['48%', '68%'],
        center: ['50%', '46%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
        label: { show: false },
        data: data.map((d, i) => ({ name: d.name, value: d.count, itemStyle: { color: ORG_COLORS[i % ORG_COLORS.length] } })),
      },
    ],
  }
})

const ageOption = computed(() => {
  const data = (fleet.value?.ageDistribution || []).slice().sort((a, b) => +a.label - +b.label)
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(37,99,235,0.06)' } },
    },
    ...gridBase,
    xAxis: {
      type: 'category',
      data: data.map((d) => d.label),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dbe3ea' } },
      axisLabel: { fontSize: 9, interval: 3, color: '#64748b' },
    },
    yAxis: {
      type: 'value',
      axisLabel: { fontSize: 11, color: '#64748b' },
      splitLine: { lineStyle: { color: '#eef2f6' } },
    },
    series: [
      {
        type: 'bar',
        data: data.map((d) => d.value),
        itemStyle: { color: '#0ea5e9', borderRadius: [3, 3, 0, 0] },
        barMaxWidth: 16,
      },
    ],
  }
})

const cards = computed(() => fleet.value?.cards || {})
const pillTone = (r) => (r === 'Compatible' ? 'ok' : r === 'Review' ? 'warn' : 'bad')
const capacityChart = ref(null)
const mixChart = ref(null)
const ageChart = ref(null)

function buildPayload() {
  if (!fleet.value) throw new Error('Data not loaded yet')
  const c = fleet.value.cards
  const charts = [capacityChart.value?.getDataURL(), mixChart.value?.getDataURL(), ageChart.value?.getDataURL()]
    .filter(Boolean)
    .map((d, i) => ({
      title: ['Transformer capacity by voltage level', 'Fleet mix by manufacturer', 'Age distribution'][i],
      ...d,
    }))
  const tables = [
    {
      name: 'Overview',
      headers: ['Metric', 'Value'],
      rows: [
        ['Total transformers', c.totalTransformers],
        ['Total rated capacity (MVA)', c.totalRatedCapacityMva],
        ['Nameplate coverage (%)', c.nameplatePct],
        ['Average fleet age (years)', c.avgFleetAge],
      ],
    },
    {
      name: 'Capacity by voltage level',
      headers: ['Voltage level', 'Capacity (MVA)'],
      rows: fleet.value.capacityByVoltage.map((d) => [d.label, d.value]),
    },
    {
      name: 'Fleet mix by manufacturer',
      headers: ['Manufacturer', 'Assets', 'Share (%)'],
      rows: fleet.value.topManufacturers.map((d) => [d.name, d.count, d.pct]),
    },
    {
      name: 'Age distribution',
      headers: ['Manufacturing year', 'Transformers'],
      rows: fleet.value.ageDistribution.map((d) => [d.label, d.value]),
    },
  ]
  if (pairs.value) {
    tables.push(
      {
        name: 'Parallel pairs - summary',
        headers: ['Metric', 'Value'],
        rows: [
          ['Total pairs', pairs.value.totalPairs],
          ['Compatible', pairs.value.compatible],
          ['Review', pairs.value.review],
          ['Not compatible', pairs.value.notCompatible],
          ['Max Uk% diff', pairs.value.maxUkDiff],
        ],
      },
      {
        name: 'Parallel pairs - detail',
        headers: ['Transformer A', 'Transformer B', 'Site A', 'Site B', 'Ratio', 'Vector group', 'Tap info', 'Uk% diff', 'Result'],
        rows: pairs.value.pairs.map((p) => [p.nameA, p.nameB, p.siteA, p.siteB, p.ratio, p.vectorGroup || '', p.tapInfo || '', p.ukDiff, p.result]),
      },
    )
  }
  return { title: 'Transformer Fleet', charts, tables }
}

registerExporter('fleet', async (fmt, el) => {
  const p = buildPayload()
  if (fmt === 'csv') exportCSV(p)
  else if (fmt === 'xlsx') exportExcel(p)
  else await exportPDF(el, p)
})
</script>

<template>
  <div class="tf">
    <div class="tf-head">
      <div>
        <h1 class="tf-title">Transformer Fleet</h1>
        <div class="tf-sub">Inventory, capacity, manufacturer profile and parallel-operation screening</div>
      </div>
      <div class="tf-head-actions">
        <div class="tf-live"><span class="tf-dot"></span>Live data</div>
        <button class="tf-back" @click="goHome">← Back to Overview</button>
      </div>
    </div>

    <FilterBar :fields="['orgId', 'city', 'ward', 'voltageLevel', 'manufacturerId', 'status']" />

    <div v-if="loading" class="tf-card tf-state">Loading…</div>
    <div v-else-if="error" class="tf-card tf-state tf-error">Error: {{ error }}</div>
    <template v-else-if="fleet">
      <div class="tf-kpis">
        <div class="tf-card tf-kpi" style="--accent:#2563eb; --accent-soft:#dbe7fd">
          <div class="tf-kpi-top"><span class="tf-ico">TT</span><span class="tf-kpi-label">Total transformers</span></div>
          <div class="tf-kpi-value">{{ fmt(cards.totalTransformers) }}</div>
          <div class="tf-kpi-sub">Power transformers in scope</div>
          <div class="tf-kpi-bar"></div>
        </div>
        <div class="tf-card tf-kpi" style="--accent:#16a34a; --accent-soft:#d8f1e2">
          <div class="tf-kpi-top"><span class="tf-ico">MW</span><span class="tf-kpi-label">Total rated capacity</span></div>
          <div class="tf-kpi-value">{{ fmt(cards.totalRatedCapacityMva) }} <span class="tf-unit">MVA</span></div>
          <div class="tf-kpi-sub">Normalised to MVA</div>
          <div class="tf-kpi-bar"></div>
        </div>
        <div class="tf-card tf-kpi" style="--accent:#f59e0b; --accent-soft:#fdf0d5">
          <div class="tf-kpi-top"><span class="tf-ico">NP</span><span class="tf-kpi-label">Nameplate coverage</span></div>
          <div class="tf-kpi-value" :style="{ color: cards.nameplatePct < 50 ? BAD : GOOD }">{{ pct(cards.nameplatePct) }}</div>
          <div class="tf-kpi-sub">Transformers with nameplate media</div>
          <div class="tf-kpi-bar"></div>
        </div>
        <div class="tf-card tf-kpi" style="--accent:#8b5cf6; --accent-soft:#e7e1fd">
          <div class="tf-kpi-top"><span class="tf-ico">AG</span><span class="tf-kpi-label">Average fleet age</span></div>
          <div class="tf-kpi-value">{{ fmt(cards.avgFleetAge) }} <span class="tf-unit">yrs</span></div>
          <div class="tf-kpi-sub">From manufacturing year</div>
          <div class="tf-kpi-bar"></div>
        </div>
        <div class="tf-card tf-kpi" style="--accent:#0ea5e9; --accent-soft:#d9f1fc">
          <div class="tf-kpi-top"><span class="tf-ico">PR</span><span class="tf-kpi-label">Parallel-ready pairs</span></div>
          <div class="tf-kpi-value">{{ pairs ? fmt(pairs.compatible) : '-' }}</div>
          <div class="tf-kpi-sub">Compatible pairs from screening</div>
          <div class="tf-kpi-bar"></div>
        </div>
      </div>

      <div class="tf-charts">
        <div class="tf-card">
          <div class="tf-card-head">
            <div class="tf-card-title"><span class="tf-bullet" style="background:#2563eb"></span>Transformer capacity by voltage level</div>
            <span class="tf-card-tag">MVA</span>
          </div>
          <EChart ref="capacityChart" :option="capacityOption" height="280px" />
        </div>
        <div class="tf-card">
          <div class="tf-card-head">
            <div class="tf-card-title"><span class="tf-bullet" style="background:#16a34a"></span>Fleet mix by manufacturer</div>
            <span class="tf-card-tag">{{ fmt(mixTotal) }} units</span>
          </div>
          <EChart ref="mixChart" :option="mixOption" height="280px" />
        </div>
        <div class="tf-card">
          <div class="tf-card-head">
            <div class="tf-card-title"><span class="tf-bullet" style="background:#0ea5e9"></span>Age distribution</div>
            <span class="tf-card-tag">by manufacturing year</span>
          </div>
          <EChart ref="ageChart" :option="ageOption" height="280px" />
        </div>
      </div>
    </template>

    <div class="tf-card tf-compat">
      <div class="tf-compat-head">
        <div>
          <div class="tf-card-title"><span class="tf-bullet" style="background:#f59e0b"></span>Parallel operation compatibility</div>
          <div class="tf-sub" style="margin-top:4px">Technical screening of transformer pairs</div>
        </div>
        <button class="tf-btn" @click="fetchPairs">Recalculate</button>
      </div>

      <div class="tf-criteria">
        <button
          v-for="c in criteria"
          :key="c.key"
          class="tf-criterion"
          :class="{ active: activeCriterion === c.key }"
          @click="activeCriterion = activeCriterion === c.key ? '' : c.key"
        >
          <strong>{{ c.label }}</strong>
          <span v-if="c.count > 0" class="tf-count-bad">{{ fmt(c.count) }} failing</span>
          <span v-else class="tf-count-ok">No issues</span>
          <p>{{ c.desc }}</p>
        </button>
      </div>

      <div class="tf-controls">
        <div>
          <label class="tf-label">Maximum Uk% difference</label>
          <input v-model.number="parCfg.maxUkDiff" type="number" min="0" max="100" class="tf-input" />
        </div>
        <div class="tf-checks">
          <label><input v-model="parCfg.sameSite" type="checkbox" /> Same site only</label>
          <label><input v-model="parCfg.requireTapOverlap" type="checkbox" /> Require tap overlap</label>
          <label>Max power ratio
            <input v-model.number="parCfg.maxPowerRatio" type="number" min="0" step="0.1" class="tf-input tf-input-sm" />
          </label>
        </div>
        <button class="tf-btn" @click="fetchPairs">Recalculate</button>
      </div>

      <div v-if="loadingPairs" class="tf-state-box">Loading pairs…</div>
      <div v-else-if="errorPairs" class="tf-state-box tf-error">Error: {{ errorPairs }}</div>
      <template v-else-if="pairs">
        <div class="tf-pairs">
          <div class="tf-pair"><span class="tf-pair-label">Total pairs</span><strong>{{ fmt(pairs.totalPairs) }}</strong></div>
          <div class="tf-pair"><span class="tf-pair-label">Compatible</span><strong class="tf-ok">{{ fmt(pairs.compatible) }}</strong></div>
          <div class="tf-pair"><span class="tf-pair-label">Review</span><strong class="tf-warn">{{ fmt(pairs.review) }}</strong></div>
          <div class="tf-pair"><span class="tf-pair-label">Not compatible</span><strong class="tf-bad">{{ fmt(pairs.notCompatible) }}</strong></div>
        </div>
        <div v-if="activeCriterion" class="tf-filter-note">
          Filtered to {{ fmt(visiblePairs.length) }} pair(s) failing
          <strong>{{ criteria.find((c) => c.key === activeCriterion)?.label }}</strong>
          — <button class="tf-link" @click="activeCriterion = ''">clear</button>
        </div>
        <div class="tf-scroll">
          <table class="tf-table">
            <thead>
              <tr><th>Transformer A</th><th>Transformer B</th><th>Site</th><th>Ratio</th><th>Vector group</th><th>Tap info</th><th>Uk% diff</th><th>Result</th></tr>
            </thead>
            <tbody>
              <tr v-for="p in visiblePairs" :key="p.id">
                <td class="tf-name">{{ p.nameA }}</td>
                <td class="tf-name">{{ p.nameB }}</td>
                <td>{{ p.siteA }}<template v-if="p.siteB && p.siteB !== p.siteA"> / {{ p.siteB }}</template></td>
                <td>{{ p.ratio }}</td>
                <td>{{ p.vectorGroup || '-' }}</td>
                <td>{{ p.tapInfo || '-' }}</td>
                <td class="tf-num">{{ p.ukDiff }}</td>
                <td><span class="tf-pill" :class="pillTone(p.result)">{{ p.result }}</span></td>
              </tr>
              <tr v-if="!visiblePairs.length">
                <td colspan="8" style="text-align:center;color:#94a3b8">No pairs in scope.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.tf {
  background: #f1f5f9;
  border-radius: 12px;
  padding: 4px 4px 12px;
}

.tf-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin: 2px 2px 14px;
  gap: 10px;
}

.tf-title {
  font-size: 22px;
  font-weight: 800;
  color: #0f2a5c;
  margin: 0;
  letter-spacing: -0.01em;
}

.tf-sub {
  font-size: 12.5px;
  color: #64748b;
  margin-top: 4px;
}

.tf-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.tf-live {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  padding: 6px 13px;
  font-size: 11.5px;
  font-weight: 700;
  color: #16a34a;
  box-shadow: 0 1px 2px rgba(15, 42, 92, 0.06);
  white-space: nowrap;
}

.tf-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #16a34a;
  box-shadow: 0 0 0 3px rgba(22, 163, 74, 0.18);
}

.tf-back {
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #0f2a5c;
  padding: 7px 13px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}

.tf-back:hover {
  border-color: #93c5fd;
  color: #1d4ed8;
}

.tf-card {
  background: #fff;
  border: 1px solid #e5ebf1;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(15, 42, 92, 0.06);
  min-width: 0;
}

.tf-state { font-size: 13px; color: #64748b; }
.tf-error { color: #dc2626; }

.tf-kpis {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.tf-kpi {
  position: relative;
  overflow: hidden;
  padding-bottom: 18px;
}

.tf-kpi-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tf-ico {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 800;
  font-size: 11px;
  flex-shrink: 0;
}

.tf-kpi-label {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #64748b;
  line-height: 1.4;
}

.tf-kpi-value {
  font-size: 29px;
  font-weight: 800;
  color: #0f2a5c;
  margin-top: 10px;
  letter-spacing: -0.02em;
  line-height: 1;
  white-space: nowrap;
}

.tf-unit {
  font-size: 13px;
  font-weight: 700;
  color: #94a3b8;
}

.tf-kpi-sub {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 6px;
  line-height: 1.4;
}

.tf-kpi-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 4px;
  background: var(--accent);
}

.tf-charts {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.tf-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.tf-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 750;
  color: #0f2a5c;
}

.tf-bullet {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

.tf-card-tag {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  background: #f1f5f9;
  border-radius: 999px;
  padding: 3px 10px;
  white-space: nowrap;
}

.tf-compat { margin-bottom: 0; }

.tf-compat-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 12px;
}

.tf-btn {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #fff;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}

.tf-btn:hover { background: #1d4ed8; }

.tf-criteria {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}

.tf-criterion {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 10px;
  padding: 12px;
  min-height: 96px;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  color: inherit;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.tf-criterion:hover { border-color: #93c5fd; }

.tf-criterion.active {
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.18);
  background: #fff;
}

.tf-criterion strong { font-size: 12.5px; color: #0f2a5c; }

.tf-criterion p {
  margin: 6px 0 0;
  color: #94a3b8;
  font-size: 11px;
  line-height: 1.4;
}

.tf-count-bad {
  display: inline-block;
  margin-left: 6px;
  background: #fde7ea;
  color: #b91c1c;
  border-radius: 999px;
  padding: 2px 9px;
  font-size: 10.5px;
  font-weight: 750;
}

.tf-count-ok {
  display: inline-block;
  margin-left: 6px;
  background: #d8f1e2;
  color: #15803d;
  border-radius: 999px;
  padding: 2px 9px;
  font-size: 10.5px;
  font-weight: 750;
}

.tf-controls {
  display: grid;
  grid-template-columns: 190px 1fr auto;
  gap: 15px;
  align-items: end;
  background: #f8fafc;
  border: 1px solid #eef2f6;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 12px;
}

.tf-label {
  display: block;
  font-size: 10.5px;
  font-weight: 700;
  color: #64748b;
  margin-bottom: 6px;
}

.tf-input {
  width: 100%;
  height: 36px;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
  background: #fff;
  padding: 0 10px;
  color: #0f2a5c;
}

.tf-input-sm { width: 76px; }

.tf-checks {
  display: flex;
  gap: 18px;
  align-items: center;
  min-height: 36px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #475569;
}

.tf-checks label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  font-weight: 600;
}

.tf-checks input[type='checkbox'] {
  width: 15px;
  height: 15px;
  accent-color: #2563eb;
}

.tf-state-box {
  font-size: 13px;
  color: #64748b;
  background: #f8fafc;
  border: 1px solid #eef2f6;
  border-radius: 8px;
  padding: 14px;
}

.tf-pairs {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}

.tf-pair {
  background: #f8fafc;
  border: 1px solid #eef2f6;
  border-radius: 10px;
  padding: 12px 14px;
}

.tf-pair-label {
  display: block;
  font-size: 10.5px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #94a3b8;
}

.tf-pair strong {
  display: block;
  font-size: 24px;
  font-weight: 800;
  color: #0f2a5c;
  margin-top: 6px;
}

.tf-pair strong.tf-ok { color: #16a34a; }
.tf-pair strong.tf-warn { color: #d97706; }
.tf-pair strong.tf-bad { color: #dc2626; }

.tf-filter-note {
  font-size: 12px;
  color: #475569;
  background: #dbe7fd;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  padding: 9px 12px;
  margin-bottom: 12px;
}

.tf-link {
  border: 0;
  background: none;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  padding: 0;
}

.tf-scroll {
  max-height: 380px;
  overflow-y: auto;
  border: 1px solid #eef2f6;
  border-radius: 8px;
}

.tf-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.tf-table th {
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #94a3b8;
  text-align: left;
  padding: 9px 10px;
  border-bottom: 1px solid #eef2f6;
  background: #f8fafc;
  position: sticky;
  top: 0;
  z-index: 2;
}

.tf-table td {
  padding: 10px;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
  vertical-align: middle;
}

.tf-table tbody tr:last-child td { border-bottom: none; }
.tf-table tbody tr:hover { background: #f8fafc; }

.tf-name { font-weight: 600; color: #1e293b; }
.tf-num { font-variant-numeric: tabular-nums; }

.tf-pill {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 10.5px;
  font-weight: 750;
  white-space: nowrap;
}

.tf-pill.ok { background: #d8f1e2; color: #15803d; }
.tf-pill.warn { background: #fdf0d5; color: #b45309; }
.tf-pill.bad { background: #fde7ea; color: #b91c1c; }

@media (max-width: 1400px) {
  .tf-kpis { grid-template-columns: repeat(3, 1fr); }
  .tf-charts { grid-template-columns: 1fr 1fr; }
  .tf-criteria { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 900px) {
  .tf-charts { grid-template-columns: 1fr; }
  .tf-pairs { grid-template-columns: repeat(2, 1fr); }
  .tf-controls { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .tf-kpis { grid-template-columns: repeat(2, 1fr); }
  .tf-head { flex-direction: column; }
  .tf-criteria { grid-template-columns: 1fr; }
}
</style>
