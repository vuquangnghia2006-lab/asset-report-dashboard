<script setup>
import * as echarts from 'echarts'
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '280px' },
})

const emit = defineEmits(['click'])

const el = ref(null)
let chart = null
let ro = null

onMounted(() => {
  chart = echarts.init(el.value)
  chart.setOption(props.option)
  chart.on('click', (params) => emit('click', params))
  ro = new ResizeObserver(() => chart && chart.resize())
  ro.observe(el.value)
})

watch(
  () => props.option,
  (o) => {
    if (chart) chart.setOption(o, true)
  },
  { deep: true },
)

onBeforeUnmount(() => {
  if (ro) ro.disconnect()
  if (chart) {
    chart.dispose()
    chart = null
  }
})

defineExpose({
  getDataURL() {
    if (!chart || !el.value) return null
    const url = chart.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#fff' })
    return { url, width: el.value.offsetWidth, height: el.value.offsetHeight }
  },
})
</script>

<template>
  <div ref="el" :style="{ height }"></div>
</template>
