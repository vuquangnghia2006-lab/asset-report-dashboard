import { createRouter, createWebHistory } from 'vue-router'
import AssetOverview from '../views/AssetOverview.vue'
import TransformerFleet from '../views/TransformerFleet.vue'
import AssetDataQuality from '../views/AssetDataQuality.vue'

const routes = [
  { path: '/', redirect: '/asset-overview' },
  { path: '/asset-overview', name: 'overview', component: AssetOverview, meta: { title: 'Asset Overview' } },
  { path: '/transformer-fleet', name: 'fleet', component: TransformerFleet, meta: { title: 'Transformer Fleet' } },
  { path: '/data-quality', name: 'quality', component: AssetDataQuality, meta: { title: 'Asset Data Quality' } },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})
