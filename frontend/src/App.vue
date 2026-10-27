<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { exportStore } from './exportStore'

const route = useRoute()
const mainEl = ref(null)
const menuOpen = ref(false)

const nav = [
  { type: 'divider', label: 'Electric Power' },
  { path: '/asset-overview', label: 'Asset Overview' },
  { path: '/transformer-fleet', label: 'Transformer Fleet' },
  { path: '/data-quality', label: 'Data Quality' },
]

const isActive = (p) => route.path.startsWith(p)

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function onDocClick() {
  menuOpen.value = false
}

async function handleExport(fmt) {
  menuOpen.value = false
  const handler = exportStore.handlers[route.name]
  if (!handler) {
    alert('Chua co du lieu de export.')
    return
  }
  try {
    await handler(fmt, mainEl.value)
  } catch (e) {
    alert('Export that bai: ' + (e.message || e))
  }
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <header class="topbar">
    <div class="brand">
      <div class="logo">AM</div>
      <div>
        <div class="brand-title">Asset & Mining Management</div>
        <div class="brand-sub">Electric power assets & mining operations</div>
      </div>
    </div>
    <div class="top-actions">
      <div class="export-menu">
        <button class="btn secondary" @click.stop="toggleMenu">Export ▾</button>
        <div v-if="menuOpen" class="export-dropdown" @click.stop>
          <button @click="handleExport('pdf')">Export PDF</button>
          <button @click="handleExport('xlsx')">Export Excel (.xlsx)</button>
          <button @click="handleExport('csv')">Export CSV</button>
        </div>
      </div>
    </div>
  </header>

  <div class="shell">
    <aside class="sidebar">
      <template v-for="item in nav" :key="item.label">
        <div v-if="item.type === 'divider'" class="nav-label">{{ item.label }}</div>
        <router-link
          v-else
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          {{ item.label }}
        </router-link>
      </template>
    </aside>

    <main ref="mainEl" class="main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.nav-item {
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 9px;
}
</style>
