<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { dataQuality } from '../api'
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
const PURPLE = '#8b5cf6'
const FALLBACK = ['#2563eb', '#16a34a', '#f59e0b', '#8b5cf6', '#0ea5e9', '#ef4444']

const report = ref(null)
const loading = ref(false)
const error = ref(null)
const activeTab = ref('Transformer')

async function fetchData() {
  loading.value = true
  error.value = null
  try {
    report.value = await dataQuality(toParams(store.filters))
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

watch(() => store.filters, fetchData)
onMounted(fetchData)

const cards = computed(() => report.value?.cards || {})

function toneByThreshold(v, hi = 80, mid = 50) {
  const n = Number(v) || 0
  if (n >= hi) return 'ok'
  if (n >= mid) return 'warn'
  return 'bad'
}

const TONE_COLOR = { ok: GOOD, warn: WARN, bad: BAD, info: BLUE }

const catOption = computed(() => {
  const data = report.value?.completenessByCategory || []
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(37,99,235,0.06)' } },
      formatter: (ps) => `${ps[0].name}<br/>${ps[0].value}% complete`,
    },
    grid: { left: 44, right: 16, top: 30, bottom: 34 },
    xAxis: {
      type: 'category',
      data: data.map((d) => d.label),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dbe3ea' } },
      axisLabel: { fontSize: 10, interval: 0, rotate: 18, color: '#64748b' },
    },
    yAxis: {
      type: 'value',
      name: '%',
      max: 100,
      nameTextStyle: { color: '#94a3b8', fontSize: 11 },
      axisLabel: { fontSize: 11, color: '#64748b' },
      splitLine: { lineStyle: { color: '#eef2f6' } },
    },
    series: [
      {
        type: 'bar',
        data: data.map((d) => d.value),
        itemStyle: {
          color: (p) => (p.value >= 80 ? GOOD : p.value >= 50 ? WARN : BAD),
          borderRadius: [6, 6, 0, 0],
        },
        label: {
          show: true,
          position: 'top',
          fontSize: 11,
          fontWeight: 700,
          color: '#334155',
          formatter: (p) => `${p.value}%`,
        },
        barMaxWidth: 56,
      },
    ],
  }
})

function statusColor(label, i) {
  const s = String(label || '').toLowerCase()
  if (s.includes('incomplete')) return WARN
  if (s.includes('invalid') || s.includes('missing') || s.includes('mismatch')) return BAD
  if (s.includes('complete') || s.includes('balanced') || s.includes('valid')) return GOOD
  return FALLBACK[i % FALLBACK.length]
}

const statusTotal = computed(() =>
  (report.value?.statusDistribution || []).reduce((sum, d) => sum + (Number(d.value) || 0), 0),
)

const statusOption = computed(() => {
  const data = report.value?.statusDistribution || []
  const total = statusTotal.value || 0
  return {
    tooltip: {
      trigger: 'item',
      formatter: (p) => {
        const share = total ? Math.round((1000 * p.value) / total) / 10 : 0
        return `${p.name}<br/>${fmt(p.value)} assets &middot; ${share}%`
      },
    },
    title: {
      text: fmt(total),
      subtext: 'assets',
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
        const item = data.find((d) => d.label === name)
        const share = total && item ? Math.round((1000 * item.value) / total) / 10 : 0
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
        data: data.map((d, i) => ({ name: d.label, value: d.value, itemStyle: { color: statusColor(d.label, i) } })),
      },
    ],
  }
})

const tabs = computed(() => {
  const set = new Set((report.value?.missingKeyFields || []).map((m) => m.assetClass))
  return ['Transformer', 'Circuit Breaker', 'Current Transformer (CT)', 'Voltage Transformer (VT)', 'Disconnector', 'Surge Arrester', 'Bushing'].filter((c) => set.has(c))
})

const visibleMissing = computed(() => {
  const all = report.value?.missingKeyFields || []
  const cls = activeTab.value
  if (!all.some((m) => m.assetClass === cls) && tabs.value.length) {
    return all.filter((m) => m.assetClass === tabs.value[0])
  }
  return all.filter((m) => m.assetClass === cls)
})

const phaseStatusTone = (s) => (s === 'Balanced' ? 'ok' : s === 'Mismatch' ? 'bad' : 'info')

const phaseTotals = computed(() => {
  const rows = report.value?.phaseBalance || []
  return {
    a: rows.reduce((s, r) => s + (r.phaseA || 0), 0),
    b: rows.reduce((s, r) => s + (r.phaseB || 0), 0),
    c: rows.reduce((s, r) => s + (r.phaseC || 0), 0),
  }
})

function missTone(v) {
  const n = Number(v) || 0
  if (n > 10) return 'bad'
  if (n > 5) return 'warn'
  return 'ok'
}

const catChart = ref(null)
const statusChart = ref(null)

function buildPayload() {
  if (!report.value) throw new Error('Data not loaded yet')
  const c = report.value.cards
  const charts = [catChart.value?.getDataURL(), statusChart.value?.getDataURL()]
    .filter(Boolean)
    .map((d, i) => ({
      title: i === 0 ? 'Completeness by category' : 'Status distribution',
      ...d,
    }))
  return {
    title: 'Asset Data Quality',
    charts,
    tables: [
      {
        name: 'Overview',
        headers: ['Metric', 'Value'],
        rows: [
          ['Overall completeness (%)', c.overallCompletenessPct],
          ['Assets with missing fields', c.assetsWithMissingFields],
          ['Missing nameplates', c.missingNameplates],
          ['Phase-monitored assets', c.phaseMonitoredAssets],
          ['Balanced groups (%)', c.balancedGroupsPct],
          ['Phase mismatches', c.phaseMismatches],
        ],
      },
      {
        name: 'Completeness by category',
        headers: ['Category', 'Completeness (%)'],
        rows: report.value.completenessByCategory.map((d) => [d.label, d.value]),
      },
      {
        name: 'Status distribution',
        headers: ['Status', 'Assets'],
        rows: report.value.statusDistribution.map((d) => [d.label, d.value]),
      },
      {
        name: 'Phase balance',
        headers: ['Asset class', 'Phase A', 'Phase B', 'Phase C', 'Status'],
        rows: report.value.phaseBalance.map((r) => [r.assetClass, r.phaseA, r.phaseB, r.phaseC, r.status]),
      },
      {
        name: 'Missing key fields',
        headers: ['Asset class', 'Field', 'Category', 'Missing', 'Total', 'Missing %'],
        rows: report.value.missingKeyFields.map((m) => [m.assetClass, m.field, m.category, m.missing, m.total, m.missingPct]),
      },
    ],
  }
}

registerExporter('quality', async (fmt, el) => {
  const p = buildPayload()
  if (fmt === 'csv') exportCSV(p)
  else if (fmt === 'xlsx') exportExcel(p)
  else await exportPDF(el, p)
})
</script>

<template>
  <div class="dq">
    <div class="dq-head">
      <div>
        <h1 class="dq-title">Asset Data Quality</h1>
        <div class="dq-sub">Completeness, key-field coverage and phase-balance monitoring</div>
      </div>
      <div class="dq-live"><span class="dq-dot"></span>Live data</div>
    </div>

    <FilterBar />

    <div v-if="loading" class="dq-card dq-state">Loading…</div>
    <div v-else-if="error" class="dq-card dq-state dq-error">Error: {{ error }}</div>
    <template v-else-if="report">
      <div class="dq-kpis">
        <div class="dq-card dq-kpi" :style="{ '--accent': TONE_COLOR[toneByThreshold(cards.overallCompletenessPct)], '--accent-soft': '#e4f4ea' }">
          <div class="dq-kpi-top"><span class="dq-ico">OC</span><span class="dq-kpi-label">Overall completeness</span></div>
          <div class="dq-kpi-value" :style="{ color: TONE_COLOR[toneByThreshold(cards.overallCompletenessPct)] }">{{ pct(cards.overallCompletenessPct) }}</div>
          <div class="dq-kpi-sub">Weighted across key fields</div>
          <div class="dq-kpi-bar"></div>
        </div>
        <div class="dq-card dq-kpi" :style="{ '--accent': BAD, '--accent-soft': '#fde7ea' }">
          <div class="dq-kpi-top"><span class="dq-ico">MF</span><span class="dq-kpi-label">Assets with missing fields</span></div>
          <div class="dq-kpi-value">{{ fmt(cards.assetsWithMissingFields) }}</div>
          <div class="dq-kpi-sub">At least one key field missing</div>
          <div class="dq-kpi-bar"></div>
        </div>
        <div class="dq-card dq-kpi" :style="{ '--accent': WARN, '--accent-soft': '#fdf0d5' }">
          <div class="dq-kpi-top"><span class="dq-ico">NP</span><span class="dq-kpi-label">Missing nameplates</span></div>
          <div class="dq-kpi-value">{{ fmt(cards.missingNameplates) }}</div>
          <div class="dq-kpi-sub">No nameplate media attached</div>
          <div class="dq-kpi-bar"></div>
        </div>
        <div class="dq-card dq-kpi" :style="{ '--accent': BLUE, '--accent-soft': '#dbe7fd' }">
          <div class="dq-kpi-top"><span class="dq-ico">PM</span><span class="dq-kpi-label">Phase-monitored assets</span></div>
          <div class="dq-kpi-value">{{ fmt(cards.phaseMonitoredAssets) }}</div>
          <div class="dq-kpi-sub">Phase tag present on asset</div>
          <div class="dq-kpi-bar"></div>
        </div>
        <div class="dq-card dq-kpi" :style="{ '--accent': TONE_COLOR[toneByThreshold(cards.balancedGroupsPct)], '--accent-soft': '#e4f4ea' }">
          <div class="dq-kpi-top"><span class="dq-ico">BG</span><span class="dq-kpi-label">Balanced groups</span></div>
          <div class="dq-kpi-value" :style="{ color: TONE_COLOR[toneByThreshold(cards.balancedGroupsPct)] }">{{ pct(cards.balancedGroupsPct) }}</div>
          <div class="dq-kpi-sub">Equal A/B/C counts per group</div>
          <div class="dq-kpi-bar"></div>
        </div>
        <div class="dq-card dq-kpi" :style="{ '--accent': BAD, '--accent-soft': '#fde7ea' }">
          <div class="dq-kpi-top"><span class="dq-ico">MM</span><span class="dq-kpi-label">Phase mismatches</span></div>
          <div class="dq-kpi-value">{{ fmt(cards.phaseMismatches) }}</div>
          <div class="dq-kpi-sub">Groups requiring review</div>
          <div class="dq-kpi-bar"></div>
        </div>
      </div>

      <div class="dq-charts">
        <div class="dq-card">
          <div class="dq-card-head">
            <div class="dq-card-title"><span class="dq-bullet" style="background:#16a34a"></span>Completeness by category</div>
            <span class="dq-card-tag">% complete</span>
          </div>
          <EChart ref="catChart" :option="catOption" height="280px" />
        </div>
        <div class="dq-card">
          <div class="dq-card-head">
            <div class="dq-card-title"><span class="dq-bullet" style="background:#2563eb"></span>Quality status distribution</div>
            <span class="dq-card-tag">{{ fmt(statusTotal) }} assets</span>
          </div>
          <EChart ref="statusChart" :option="statusOption" height="280px" />
        </div>
      </div>

      <div class="dq-tables">
        <div class="dq-card">
          <div class="dq-card-head">
            <div class="dq-card-title"><span class="dq-bullet" style="background:#f59e0b"></span>Asset count by phase</div>
            <span class="dq-card-tag">A / B / C</span>
          </div>
          <div class="dq-phase-scroll">
            <div class="dq-phase-grid">
              <div class="dq-phase-head">Asset class</div>
              <div class="dq-phase-head">Phase A</div>
              <div class="dq-phase-head">Phase B</div>
              <div class="dq-phase-head">Phase C</div>
              <div class="dq-phase-head">Status</div>
              <template v-for="r in report.phaseBalance" :key="r.assetClass">
                <div class="dq-name">{{ r.assetClass }}</div>
                <div class="dq-pa">{{ fmt(r.phaseA) }}</div>
                <div class="dq-pb">{{ fmt(r.phaseB) }}</div>
                <div class="dq-pc">{{ fmt(r.phaseC) }}</div>
                <div><span class="dq-pill" :class="phaseStatusTone(r.status)">{{ r.status }}</span></div>
              </template>
            </div>
          </div>
          <div class="dq-phase-total">
            <div class="dq-phase-cell pa"><span>Phase A total</span><strong>{{ fmt(phaseTotals.a) }}</strong></div>
            <div class="dq-phase-cell pb"><span>Phase B total</span><strong>{{ fmt(phaseTotals.b) }}</strong></div>
            <div class="dq-phase-cell pc"><span>Phase C total</span><strong>{{ fmt(phaseTotals.c) }}</strong></div>
          </div>
        </div>

        <div class="dq-card">
          <div class="dq-card-head">
            <div class="dq-card-title"><span class="dq-bullet" style="background:#8b5cf6"></span>Missing key fields</div>
            <span class="dq-card-tag">{{ visibleMissing.length }} fields</span>
          </div>
          <div class="dq-tabs">
            <button
              v-for="t in tabs"
              :key="t"
              class="dq-tab"
              :class="{ active: activeTab === t }"
              @click="activeTab = t"
            >
              {{ t }}
            </button>
          </div>
          <div class="dq-note">Each asset-type tab includes the common key fields plus its asset-specific key fields.</div>
          <div class="dq-scroll">
            <table class="dq-table">
              <thead>
                <tr><th>Field</th><th>Category</th><th style="width:110px">Missing</th><th style="width:150px">Missing %</th></tr>
              </thead>
              <tbody>
                <tr v-for="m in visibleMissing" :key="m.assetClass + m.field">
                  <td class="dq-name">{{ m.field }}</td>
                  <td>{{ m.category }}</td>
                  <td class="dq-num">{{ fmt(m.missing) }} <span class="dq-dim">/ {{ fmt(m.total) }}</span></td>
                  <td>
                    <div class="dq-share">
                      <div class="dq-track"><span :class="missTone(m.missingPct)" :style="{ width: Math.min(100, Number(m.missingPct) || 0) + '%' }"></span></div>
                      <span class="dq-pct" :class="missTone(m.missingPct)">{{ pct(m.missingPct) }}</span>
                    </div>
                  </td>
                </tr>
                <tr v-if="!visibleMissing.length">
                  <td colspan="4" style="text-align:center;color:#94a3b8">No data.</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.dq {
  background: #f1f5f9;
  border-radius: 12px;
  padding: 4px 4px 12px;
}

.dq-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin: 2px 2px 14px;
}

.dq-title {
  font-size: 22px;
  font-weight: 800;
  color: #0f2a5c;
  margin: 0;
  letter-spacing: -0.01em;
}

.dq-sub {
  font-size: 12.5px;
  color: #64748b;
  margin-top: 4px;
}

.dq-live {
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

.dq-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #16a34a;
  box-shadow: 0 0 0 3px rgba(22, 163, 74, 0.18);
}

.dq-card {
  background: #fff;
  border: 1px solid #e5ebf1;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(15, 42, 92, 0.06);
  min-width: 0;
}

.dq-state { font-size: 13px; color: #64748b; }
.dq-error { color: #dc2626; }

.dq-kpis {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.dq-kpi {
  position: relative;
  overflow: hidden;
  padding-bottom: 18px;
}

.dq-kpi-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dq-ico {
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

.dq-kpi-label {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #64748b;
  line-height: 1.4;
}

.dq-kpi-value {
  font-size: 29px;
  font-weight: 800;
  color: #0f2a5c;
  margin-top: 10px;
  letter-spacing: -0.02em;
  line-height: 1;
  white-space: nowrap;
}

.dq-kpi-sub {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 6px;
  line-height: 1.4;
}

.dq-kpi-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 4px;
  background: var(--accent);
}

.dq-charts {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 12px;
  margin-bottom: 12px;
}

.dq-tables {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.dq-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.dq-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 750;
  color: #0f2a5c;
}

.dq-bullet {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

.dq-card-tag {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  background: #f1f5f9;
  border-radius: 999px;
  padding: 3px 10px;
  white-space: nowrap;
}

.dq-phase-scroll {
  max-height: 342px;
  overflow-y: auto;
  border: 1px solid #eef2f6;
  border-radius: 8px;
}

.dq-phase-grid {
  display: grid;
  grid-template-columns: 1.4fr repeat(3, 1fr) 0.9fr;
  font-size: 12px;
  min-width: 520px;
}

.dq-phase-grid > div {
  padding: 10px;
  border-bottom: 1px solid #f1f5f9;
}

.dq-phase-head {
  background: #f8fafc;
  font-size: 10.5px;
  font-weight: 750;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #94a3b8;
  position: sticky;
  top: 0;
  z-index: 2;
}

.dq-name { font-weight: 600; color: #1e293b; }
.dq-pa { background: #eff6ff; font-variant-numeric: tabular-nums; }
.dq-pb { background: #f0fdf4; font-variant-numeric: tabular-nums; }
.dq-pc { background: #fffbeb; font-variant-numeric: tabular-nums; }

.dq-pill {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 10.5px;
  font-weight: 750;
  white-space: nowrap;
}

.dq-pill.ok { background: #d8f1e2; color: #15803d; }
.dq-pill.bad { background: #fde7ea; color: #b91c1c; }
.dq-pill.info { background: #dbe7fd; color: #1d4ed8; }

.dq-phase-total {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 9px;
  margin-top: 12px;
}

.dq-phase-cell {
  border-radius: 8px;
  padding: 10px;
  text-align: center;
}

.dq-phase-cell span {
  display: block;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #64748b;
}

.dq-phase-cell strong {
  display: block;
  font-size: 20px;
  font-weight: 800;
  color: #0f2a5c;
  margin-top: 4px;
}

.dq-phase-cell.pa { background: #eff6ff; }
.dq-phase-cell.pb { background: #f0fdf4; }
.dq-phase-cell.pc { background: #fffbeb; }

.dq-tabs {
  display: flex;
  gap: 7px;
  flex-wrap: wrap;
  margin: 2px 0 10px;
}

.dq-tab {
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #64748b;
  padding: 7px 13px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
}

.dq-tab:hover {
  border-color: #93c5fd;
  color: #1d4ed8;
}

.dq-tab.active {
  background: #0f2a5c;
  border-color: #0f2a5c;
  color: #fff;
}

.dq-note {
  font-size: 11px;
  color: #94a3b8;
  margin: -2px 0 10px;
}

.dq-scroll {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #eef2f6;
  border-radius: 8px;
}

.dq-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.dq-table th {
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

.dq-table td {
  padding: 10px;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
  vertical-align: middle;
}

.dq-table tbody tr:last-child td { border-bottom: none; }
.dq-table tbody tr:hover { background: #f8fafc; }

.dq-num { font-variant-numeric: tabular-nums; }
.dq-dim { color: #94a3b8; }

.dq-share {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dq-track {
  flex: 1;
  height: 7px;
  background: #eef2f6;
  border-radius: 99px;
  overflow: hidden;
  min-width: 50px;
}

.dq-track span {
  display: block;
  height: 100%;
  border-radius: 99px;
  background: #2563eb;
}

.dq-track span.bad { background: #ef4444; }
.dq-track span.warn { background: #f59e0b; }
.dq-track span.ok { background: #16a34a; }

.dq-pct {
  font-size: 11.5px;
  font-weight: 750;
  color: #0f2a5c;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.dq-pct.bad { color: #dc2626; }
.dq-pct.warn { color: #d97706; }
.dq-pct.ok { color: #16a34a; }

@media (max-width: 1400px) {
  .dq-kpis { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 1200px) {
  .dq-charts, .dq-tables { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .dq-kpis { grid-template-columns: repeat(2, 1fr); }
  .dq-head { flex-direction: column; gap: 8px; }
}
</style>
