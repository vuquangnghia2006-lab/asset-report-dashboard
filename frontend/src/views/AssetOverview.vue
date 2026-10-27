<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { assetOverview } from '../api'
import { store, toParams } from '../store'
import FilterBar from '../components/FilterBar.vue'
import EChart from '../components/EChart.vue'
import { fmt, pct } from '../format'
import { exportCSV, exportExcel, exportPDF } from '../export'
import { registerExporter } from '../exportStore'

const VOLT_COLORS = ['#2563eb', '#16a34a', '#f59e0b', '#8b5cf6', '#0ea5e9', '#ef4444']
const ORG_COLORS = ['#2563eb', '#16a34a', '#f59e0b', '#8b5cf6', '#ef4444', '#0ea5e9', '#14b8a6', '#f43f5e']

const report = ref(null)
const loading = ref(false)
const error = ref(null)

async function fetchData() {
  loading.value = true
  error.value = null
  try {
    report.value = await assetOverview(toParams(store.filters))
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

watch(() => store.filters, fetchData)
onMounted(fetchData)

const round1 = (n) => Math.round(Number(n) * 10) / 10

const mixTotal = computed(() =>
  (report.value?.assetMixByOrg || []).reduce((s, d) => s + (Number(d.value) || 0), 0),
)

const capacityOption = computed(() => {
  const data = report.value?.capacityByVoltage || []
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(37,99,235,0.06)' } },
    },
    grid: { left: 48, right: 16, top: 30, bottom: 30 },
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
          itemStyle: {
            color: VOLT_COLORS[i % VOLT_COLORS.length],
            borderRadius: [6, 6, 0, 0],
          },
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

const mixOption = computed(() => {
  const data = report.value?.assetMixByOrg || []
  const total = mixTotal.value || 0
  return {
    tooltip: {
      formatter: (p) => {
        const share = total ? round1((100 * p.value) / total) : 0
        return `${p.name}<br/>${fmt(p.value)} assets &middot; ${share}%<br/><span style="color:#bbb">Click to open fleet</span>`
      },
    },
    series: [
      {
        type: 'treemap',
        roam: false,
        nodeClick: false,
        breadcrumb: { show: false },
        label: {
          fontSize: 12,
          fontWeight: 700,
          color: '#fff',
          formatter: (p) => {
            const share = total ? round1((100 * p.value) / total) : 0
            return `${p.name}\n${share}%`
          },
        },
        upperLabel: { show: false },
        itemStyle: { borderColor: '#fff', borderWidth: 3, gapWidth: 3, borderRadius: 6, cursor: 'pointer' },
        levels: [{ itemStyle: { borderColor: '#fff', borderWidth: 3, gapWidth: 3 } }],
        data: data.map((d, i) => ({
          name: d.label,
          value: d.value,
          id: d.id,
          itemStyle: { color: ORG_COLORS[i % ORG_COLORS.length] },
        })),
      },
    ],
  }
})

const cards = computed(() => report.value?.cards || {})
const capacityChart = ref(null)
const mixChart = ref(null)
const router = useRouter()

function openOrgMix(params) {
  const id = params?.data?.id
  if (!id) return
  store.filters = { ...store.filters, orgId: String(id) }
  router.push('/transformer-fleet')
}

function missTone(v) {
  const n = Number(v) || 0
  if (n > 10) return 'bad'
  if (n > 5) return 'warn'
  return 'ok'
}

function buildPayload() {
  if (!report.value) return null
  const c = report.value.cards
  const charts = [capacityChart.value?.getDataURL(), mixChart.value?.getDataURL()]
    .filter(Boolean)
    .map((d, i) => ({
      title: i === 0 ? 'Transformer capacity by voltage level' : 'Asset mix by organisation',
      ...d,
    }))
  return {
    title: 'Asset Overview',
    charts,
    tables: [
      {
        name: 'Overview',
        headers: ['Metric', 'Value'],
        rows: [
          ['Total assets', c.totalAssets],
          ['Total substations', c.totalSubstations],
          ['Total transformers', c.totalTransformers],
          ['Transformer capacity (MVA)', c.transformerCapacityMva],
        ],
      },
      {
        name: 'Capacity by voltage level',
        headers: ['Voltage level', 'Capacity (MVA)'],
        rows: report.value.capacityByVoltage.map((d) => [d.label, d.value]),
      },
      {
        name: 'Asset mix by organisation',
        headers: ['Organisation', 'Assets'],
        rows: report.value.assetMixByOrg.map((d) => [d.label, d.value]),
      },
      {
        name: 'Top manufacturers',
        headers: ['Manufacturer', 'Assets', 'Share (%)'],
        rows: report.value.topManufacturers.map((d) => [d.name, d.count, d.pct]),
      },
      {
        name: 'Data completeness - transformers',
        headers: ['Field', 'Missing', 'Total', 'Missing %'],
        rows: report.value.dataCompleteness.map((d) => [d.field, d.missing, d.total, d.missingPct]),
      },
    ],
  }
}

registerExporter('overview', async (fmt, el) => {
  const p = buildPayload()
  if (!p) throw new Error('Data not loaded yet')
  if (fmt === 'csv') exportCSV(p)
  else if (fmt === 'xlsx') exportExcel(p)
  else await exportPDF(el, p)
})
</script>

<template>
  <div class="ov">
    <div class="ov-head">
      <div>
        <h1 class="ov-title">Asset Overview</h1>
        <div class="ov-sub">Inventory, capacity and data completeness across the asset fleet</div>
      </div>
      <div class="ov-live"><span class="ov-dot"></span>Live data</div>
    </div>

    <FilterBar />

    <div v-if="loading" class="ov-card ov-state">Loading…</div>
    <div v-else-if="error" class="ov-card ov-state ov-error">Error: {{ error }}</div>
    <template v-else-if="report">
      <div class="ov-kpis">
        <div class="ov-card ov-kpi" style="--accent:#2563eb; --accent-soft:#dbe7fd">
          <div class="ov-kpi-top">
            <span class="ov-ico">TA</span>
            <span class="ov-kpi-label">Total assets</span>
          </div>
          <div class="ov-kpi-value">{{ fmt(cards.totalAssets) }}</div>
          <div class="ov-kpi-sub">All asset classes in scope</div>
          <div class="ov-kpi-bar"></div>
        </div>
        <div class="ov-card ov-kpi" style="--accent:#16a34a; --accent-soft:#d8f1e2">
          <div class="ov-kpi-top">
            <span class="ov-ico">ST</span>
            <span class="ov-kpi-label">Total substations</span>
          </div>
          <div class="ov-kpi-value">{{ fmt(cards.totalSubstations) }}</div>
          <div class="ov-kpi-sub">Substations owning assets</div>
          <div class="ov-kpi-bar"></div>
        </div>
        <div class="ov-card ov-kpi" style="--accent:#f59e0b; --accent-soft:#fdf0d5">
          <div class="ov-kpi-top">
            <span class="ov-ico">TR</span>
            <span class="ov-kpi-label">Total transformers</span>
          </div>
          <div class="ov-kpi-value">{{ fmt(cards.totalTransformers) }}</div>
          <div class="ov-kpi-sub">Power transformers</div>
          <div class="ov-kpi-bar"></div>
        </div>
        <div class="ov-card ov-kpi" style="--accent:#8b5cf6; --accent-soft:#e7e1fd">
          <div class="ov-kpi-top">
            <span class="ov-ico">MW</span>
            <span class="ov-kpi-label">Transformer capacity</span>
          </div>
          <div class="ov-kpi-value">{{ fmt(cards.transformerCapacityMva) }} <span class="ov-unit">MVA</span></div>
          <div class="ov-kpi-sub">Rated power, normalised to MVA</div>
          <div class="ov-kpi-bar"></div>
        </div>
      </div>

      <div class="ov-charts">
        <div class="ov-card">
          <div class="ov-card-head">
            <div class="ov-card-title"><span class="ov-bullet" style="background:#2563eb"></span>Transformer capacity by voltage level</div>
            <span class="ov-card-tag">MVA</span>
          </div>
          <EChart ref="capacityChart" :option="capacityOption" height="280px" />
        </div>
        <div class="ov-card">
          <div class="ov-card-head">
            <div class="ov-card-title"><span class="ov-bullet" style="background:#16a34a"></span>Asset mix by organisation</div>
            <span class="ov-card-tag">click a block for details</span>
          </div>
          <EChart ref="mixChart" :option="mixOption" height="280px" @click="openOrgMix" />
        </div>
      </div>

      <div class="ov-tables">
        <div class="ov-card">
          <div class="ov-card-head">
            <div class="ov-card-title"><span class="ov-bullet" style="background:#f59e0b"></span>Top manufacturers</div>
            <span class="ov-card-tag">by asset count</span>
          </div>
          <table class="ov-table">
            <thead>
              <tr><th style="width:44px">#</th><th>Manufacturer</th><th style="width:90px">Assets</th><th style="width:150px">Share</th></tr>
            </thead>
            <tbody>
              <tr v-for="(m, i) in report.topManufacturers" :key="m.name">
                <td><span class="ov-rank" :class="'r' + (i + 1)">{{ i + 1 }}</span></td>
                <td class="ov-name">{{ m.name }}</td>
                <td class="ov-num">{{ fmt(m.count) }}</td>
                <td>
                  <div class="ov-share">
                    <div class="ov-track"><span :style="{ width: Math.min(100, Number(m.pct) || 0) + '%' }"></span></div>
                    <span class="ov-pct">{{ pct(m.pct) }}</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="ov-card">
          <div class="ov-card-head">
            <div class="ov-card-title"><span class="ov-bullet" style="background:#8b5cf6"></span>Data completeness — transformers</div>
            <span class="ov-card-tag">missing values</span>
          </div>
          <table class="ov-table">
            <thead>
              <tr><th>Field</th><th style="width:110px">Missing</th><th style="width:150px">Missing %</th></tr>
            </thead>
            <tbody>
              <tr v-for="d in report.dataCompleteness" :key="d.field">
                <td class="ov-name">{{ d.field }}</td>
                <td class="ov-num">{{ fmt(d.missing) }} <span class="ov-dim">/ {{ fmt(d.total) }}</span></td>
                <td>
                  <div class="ov-share">
                    <div class="ov-track"><span :class="missTone(d.missingPct)" :style="{ width: Math.min(100, Number(d.missingPct) || 0) + '%' }"></span></div>
                    <span class="ov-pct" :class="missTone(d.missingPct)">{{ pct(d.missingPct) }}</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ov {
  background: #f1f5f9;
  border-radius: 12px;
  padding: 4px 4px 12px;
}

.ov-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin: 2px 2px 14px;
}

.ov-title {
  font-size: 22px;
  font-weight: 800;
  color: #0f2a5c;
  margin: 0;
  letter-spacing: -0.01em;
}

.ov-sub {
  font-size: 12.5px;
  color: #64748b;
  margin-top: 4px;
}

.ov-live {
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

.ov-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #16a34a;
  box-shadow: 0 0 0 3px rgba(22, 163, 74, 0.18);
}

.ov-card {
  background: #fff;
  border: 1px solid #e5ebf1;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(15, 42, 92, 0.06);
  min-width: 0;
}

.ov-state {
  font-size: 13px;
  color: #64748b;
}

.ov-error {
  color: #dc2626;
}

.ov-kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.ov-kpi {
  position: relative;
  overflow: hidden;
  padding-bottom: 18px;
}

.ov-kpi-top {
  display: flex;
  align-items: center;
  gap: 9px;
}

.ov-ico {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 800;
  font-size: 12px;
  flex-shrink: 0;
}

.ov-kpi-label {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #64748b;
}

.ov-kpi-value {
  font-size: 32px;
  font-weight: 800;
  color: #0f2a5c;
  margin-top: 10px;
  letter-spacing: -0.02em;
  line-height: 1;
  white-space: nowrap;
}

.ov-unit {
  font-size: 13px;
  font-weight: 700;
  color: #94a3b8;
}

.ov-kpi-sub {
  font-size: 11.5px;
  color: #94a3b8;
  margin-top: 6px;
}

.ov-kpi-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 4px;
  background: var(--accent);
}

.ov-charts {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 12px;
  margin-bottom: 12px;
}

.ov-tables {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.ov-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.ov-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 750;
  color: #0f2a5c;
}

.ov-bullet {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

.ov-card-tag {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  background: #f1f5f9;
  border-radius: 999px;
  padding: 3px 10px;
  white-space: nowrap;
}

.ov-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.ov-table th {
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #94a3b8;
  text-align: left;
  padding: 8px 10px;
  border-bottom: 1px solid #eef2f6;
  background: none;
}

.ov-table td {
  padding: 10px;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
  vertical-align: middle;
}

.ov-table tbody tr:last-child td {
  border-bottom: none;
}

.ov-table tbody tr:hover {
  background: #f8fafc;
}

.ov-rank {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: inline-grid;
  place-items: center;
  font-size: 11px;
  font-weight: 800;
  background: #eef2f6;
  color: #64748b;
}

.ov-rank.r1 { background: #fef3c7; color: #b45309; }
.ov-rank.r2 { background: #e2e8f0; color: #475569; }
.ov-rank.r3 { background: #ffedd5; color: #c2410c; }

.ov-name {
  font-weight: 600;
  color: #1e293b;
}

.ov-num {
  font-variant-numeric: tabular-nums;
  color: #334155;
}

.ov-dim {
  color: #94a3b8;
}

.ov-share {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ov-track {
  flex: 1;
  height: 7px;
  background: #eef2f6;
  border-radius: 99px;
  overflow: hidden;
  min-width: 50px;
}

.ov-track span {
  display: block;
  height: 100%;
  background: #2563eb;
  border-radius: 99px;
}

.ov-track span.bad { background: #ef4444; }
.ov-track span.warn { background: #f59e0b; }
.ov-track span.ok { background: #16a34a; }

.ov-pct {
  font-size: 11.5px;
  font-weight: 750;
  color: #0f2a5c;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.ov-pct.bad { color: #dc2626; }
.ov-pct.warn { color: #d97706; }
.ov-pct.ok { color: #16a34a; }

@media (max-width: 1200px) {
  .ov-kpis { grid-template-columns: repeat(2, 1fr); }
  .ov-charts, .ov-tables { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .ov-kpis { grid-template-columns: 1fr; }
  .ov-head { flex-direction: column; gap: 8px; }
}
</style>
