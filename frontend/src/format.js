export function fmt(n) {
  if (n === null || n === undefined) return '-'
  return Number(n).toLocaleString('en-US')
}

export function pct(n) {
  if (n === null || n === undefined) return '-'
  return Number(n).toFixed(1) + '%'
}

export const PALETTE = [
  '#168aad',
  '#29a36a',
  '#e49b2f',
  '#7776b8',
  '#d95763',
  '#42b8cc',
  '#58b68a',
  '#d9e0e7',
  '#7d80bd',
  '#0e7490',
]

export const barBase = {
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 44, right: 12, top: 18, bottom: 30 },
  xAxis: { type: 'category', axisLabel: { fontSize: 10 } },
  yAxis: { type: 'value', axisLabel: { fontSize: 10 } },
}
