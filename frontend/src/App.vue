<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from './components/AppIcon.vue'
import { exportStore } from './exportStore'

const route = useRoute()
const mainEl = ref(null)
const menuOpen = ref(false)
const treeOpen = ref(true)
const searchOpen = ref(false)
const query = ref('')
const searchEl = ref(null)

const railItems = [
  { path: '/asset-overview', label: 'Tổng quan tài sản', icon: 'grid' },
  { path: '/transformer-fleet', label: 'Đội hình máy biến áp', icon: 'bolt' },
  { path: '/data-quality', label: 'Chất lượng dữ liệu', icon: 'list' },
]

const treeGroups = [
  {
    id: 'power',
    label: 'Điện lực',
    items: [
      { path: '/asset-overview', label: 'Tổng quan tài sản', meta: 'Assets' },
      { path: '/transformer-fleet', label: 'Đội hình máy biến áp', meta: 'Fleet' },
      { path: '/data-quality', label: 'Chất lượng dữ liệu', meta: 'Quality' },
    ],
  },
]

const openGroups = ref({ power: true })

const filteredGroups = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return treeGroups
  return treeGroups
    .map((g) => ({
      ...g,
      items: g.items.filter(
        (i) => i.label.toLowerCase().includes(q) || i.meta.toLowerCase().includes(q),
      ),
    }))
    .filter((g) => g.items.length)
})

const isActive = (p) => route.path.startsWith(p)

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function toggleGroup(id) {
  openGroups.value[id] = !openGroups.value[id]
}

function toggleTree() {
  treeOpen.value = !treeOpen.value
}

async function toggleSearch() {
  searchOpen.value = !searchOpen.value
  if (searchOpen.value) {
    treeOpen.value = true
    await nextTick()
    searchEl.value?.focus()
  } else {
    query.value = ''
  }
}

function onDocClick() {
  menuOpen.value = false
}

function onKeydown(e) {
  if (e.key === 'Escape' && searchOpen.value) {
    searchOpen.value = false
    query.value = ''
  }
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

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <header class="topbar">
    <div class="brand">
      <div class="logo">AT</div>
      <div>
        <div class="brand-title">ATDigital Report</div>
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

  <div class="shell" :class="{ 'tree-collapsed': !treeOpen }">
    <div class="workspace">
      <!-- Thanh công cụ màu xanh -->
      <nav class="icon-rail" aria-label="Thanh công cụ">
        <router-link
          v-for="item in railItems"
          :key="item.path"
          :to="item.path"
          class="rail-btn"
          :class="{ active: isActive(item.path) }"
          :title="item.label"
          :aria-label="item.label"
        >
          <AppIcon :name="item.icon" />
        </router-link>

        <div class="rail-sep" role="separator"></div>

        <button
          class="rail-btn"
          :class="{ active: treeOpen }"
          title="Ẩn/hiện cây thư mục"
          aria-label="Ẩn/hiện cây thư mục"
          :aria-pressed="treeOpen"
          @click="toggleTree"
        >
          <AppIcon name="panel" />
        </button>
        <button
          class="rail-btn"
          :class="{ active: searchOpen }"
          title="Tìm trong danh mục"
          aria-label="Tìm trong danh mục"
          :aria-pressed="searchOpen"
          @click="toggleSearch"
        >
          <AppIcon name="search" />
        </button>
      </nav>

      <!-- Cây thư mục thiết bị -->
      <aside v-show="treeOpen" class="tree-panel">
        <div class="tree-head">
          <span>Cây thư mục</span>
          <button
            class="tree-collapse"
            title="Thu gọn"
            aria-label="Thu gọn cây thư mục"
            @click="toggleTree"
          >
            <AppIcon name="chevron" />
          </button>
        </div>

        <div v-if="searchOpen" class="tree-search">
          <AppIcon name="search" :size="14" />
          <input
            ref="searchEl"
            v-model="query"
            type="text"
            placeholder="Tìm trang..."
            aria-label="Tìm trang trong danh mục"
          />
          <button
            v-if="query"
            class="tree-search-clear"
            title="Xoá"
            aria-label="Xoá tìm kiếm"
            @click="query = ''"
          >
            ×
          </button>
        </div>

        <div class="tree-scroll">
          <ul class="tree">
            <li v-for="group in filteredGroups" :key="group.id">
              <button
                class="tree-group"
                :aria-expanded="openGroups[group.id]"
                @click="toggleGroup(group.id)"
              >
                <AppIcon
                  name="chevron"
                  :size="13"
                  class="tree-chevron"
                  :class="{ open: openGroups[group.id] }"
                />
                <AppIcon name="folder" :size="15" />
                <span class="tree-group-label">{{ group.label }}</span>
                <span class="tree-count">{{ group.items.length }}</span>
              </button>

              <ul v-show="openGroups[group.id]" class="tree-children">
                <li v-for="item in group.items" :key="item.path">
                  <router-link
                    :to="item.path"
                    class="tree-item"
                    :class="{ active: isActive(item.path) }"
                  >
                    <AppIcon name="doc" :size="14" />
                    <span class="tree-item-label">{{ item.label }}</span>
                    <span class="tree-item-meta">{{ item.meta }}</span>
                  </router-link>
                </li>
              </ul>
            </li>
          </ul>

          <p v-if="!filteredGroups.length" class="tree-empty">
            Không tìm thấy trang phù hợp.
          </p>
        </div>
      </aside>
    </div>

    <main ref="mainEl" class="main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.workspace {
  display: flex;
  min-width: 0;
  background: #fff;
  border-right: 1px solid var(--line);
  position: sticky;
  top: 58px;
  height: calc(100vh - 58px);
  overflow: hidden;
}

/* ---------- Icon rail ---------- */
.icon-rail {
  width: 56px;
  flex: 0 0 56px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 0;
  background: linear-gradient(180deg, var(--blue-dark), var(--blue));
  overflow: hidden;
}

.rail-btn {
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.12);
  color: #eaf8fc;
  cursor: pointer;
  text-decoration: none;
  transition: background 0.15s, color 0.15s, transform 0.1s;
}

.rail-btn:hover {
  background: rgba(255, 255, 255, 0.26);
  color: #fff;
}

.rail-btn:active {
  transform: scale(0.95);
}

.rail-btn:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}

.rail-btn.active {
  background: #fff;
  color: var(--blue-dark);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.18);
}

.rail-sep {
  width: 26px;
  height: 1px;
  flex: 0 0 auto;
  margin: 5px 0;
  background: rgba(255, 255, 255, 0.28);
}

/* ---------- Tree panel ---------- */
.tree-panel {
  width: 214px;
  flex: 0 0 214px;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #fff;
}

.tree-head {
  height: 39px;
  flex: 0 0 39px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 0 13px;
  border-bottom: 1px solid var(--line);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #98a2ad;
}

.tree-collapse {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #98a2ad;
  cursor: pointer;
}

.tree-collapse:hover {
  background: var(--blue-soft);
  color: var(--blue-dark);
}

.tree-collapse svg {
  transform: rotate(180deg);
}

.tree-search {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 8px 8px 0;
  padding: 0 8px;
  height: 32px;
  border: 1px solid var(--line);
  border-radius: 7px;
  color: #98a2ad;
  background: #fbfdfe;
}

.tree-search:focus-within {
  border-color: #8fc6d4;
  box-shadow: 0 0 0 2px rgba(22, 138, 173, 0.12);
}

.tree-search input {
  flex: 1;
  min-width: 0;
  height: 30px;
  padding: 0;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 12px;
  color: var(--text);
}

.tree-search-clear {
  border: 0;
  background: transparent;
  color: #98a2ad;
  font-size: 15px;
  line-height: 1;
  padding: 0 2px;
  cursor: pointer;
}

.tree-search-clear:hover {
  color: var(--red);
}

.tree-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px 7px 14px;
}

.tree,
.tree-children {
  list-style: none;
  margin: 0;
  padding: 0;
}

.tree-group {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 7px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #44515d;
  font-size: 12px;
  font-weight: 750;
  text-align: left;
  cursor: pointer;
}

.tree-group:hover {
  background: #f3f7fa;
}

.tree-chevron {
  flex: 0 0 auto;
  color: #98a2ad;
  transition: transform 0.15s;
}

.tree-chevron.open {
  transform: rotate(90deg);
}

.tree-group-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-count {
  flex: 0 0 auto;
  min-width: 18px;
  padding: 1px 6px;
  border-radius: 999px;
  background: #eef3f7;
  color: #7b8794;
  font-size: 9.5px;
  font-weight: 750;
  text-align: center;
}

.tree-children {
  margin: 1px 0 6px;
  padding-left: 8px;
  border-left: 1px solid #e8eef3;
  margin-left: 13px;
}

.tree-item {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 8px;
  margin: 1px 0;
  border-radius: 7px;
  color: #4b5563;
  font-size: 12px;
  text-decoration: none;
  transition: background 0.12s, color 0.12s;
}

.tree-item svg {
  flex: 0 0 auto;
  color: #9aa9b6;
}

.tree-item:hover {
  background: #f3f7fa;
  color: var(--blue-dark);
}

.tree-item:hover svg {
  color: var(--blue);
}

.tree-item.active {
  background: var(--blue-soft);
  color: var(--blue-dark);
  font-weight: 750;
}

.tree-item.active svg {
  color: var(--blue);
}

.tree-item-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-item-meta {
  flex: 0 0 auto;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  color: #a3aeb9;
}

.tree-item.active .tree-item-meta {
  color: var(--blue);
}

.tree-empty {
  margin: 14px 8px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--muted);
  text-align: center;
}

/* ---------- Responsive ---------- */
@media (max-width: 1200px) {
  .tree-panel {
    width: 192px;
    flex-basis: 192px;
  }
}

@media (max-width: 900px) {
  .tree-panel {
    position: absolute;
    left: 56px;
    top: 0;
    bottom: 0;
    z-index: 15;
    width: 224px;
    border-right: 1px solid var(--line);
    box-shadow: 4px 0 14px rgba(25, 38, 52, 0.12);
  }

  .workspace {
    position: sticky;
  }
}
</style>
