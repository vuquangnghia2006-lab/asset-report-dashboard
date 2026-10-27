import * as XLSX from 'xlsx'
import { jsPDF } from 'jspdf'

function download(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

const safeName = (s) => String(s || 'report').replace(/[\\/:*?"<>|]/g, '_').trim()

function escapeCsv(v) {
  const s = v === null || v === undefined ? '' : String(v)
  return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
}

export function exportCSV(payload) {
  const base = safeName(payload.title)
  const parts = []
  for (const t of payload.tables) {
    parts.push(['=' + t.name + '='], t.headers.map(escapeCsv).join(','))
    for (const r of t.rows) parts.push(r.map(escapeCsv).join(','))
    parts.push('')
  }
  const blob = new Blob(['\ufeff' + parts.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  download(blob, base + '.csv')
}

export function exportExcel(payload) {
  const wb = XLSX.utils.book_new()
  for (const t of payload.tables) {
    const ws = XLSX.utils.aoa_to_sheet([t.headers, ...t.rows])
    ws['!cols'] = t.headers.map(() => ({ wch: 24 }))
    XLSX.utils.book_append_sheet(wb, ws, String(t.name).slice(0, 31) || 'Sheet')
  }
  XLSX.writeFile(wb, safeName(payload.title) + '.xlsx')
}

const PAGE_W = 210
const PAGE_H = 297
const MARGIN = 12

function drawTable(doc, table, startY) {
  const colW = (PAGE_W - MARGIN * 2) / Math.max(table.headers.length, 1)
  const rowH = 7
  const pad = 2
  let y = startY
  doc.setFontSize(7)
  doc.setFillColor(246, 248, 250)
  doc.setTextColor(90, 100, 110)
  for (let i = 0; i < table.headers.length; i++) {
    doc.rect(MARGIN + i * colW, y, colW, rowH, 'F')
    doc.text(String(table.headers[i]), MARGIN + i * colW + pad, y + rowH - 2, { maxWidth: colW - pad * 2 })
  }
  y += rowH
  doc.setTextColor(31, 41, 55)
  for (const row of table.rows) {
    if (y > PAGE_H - 14) {
      doc.addPage()
      y = 16
    }
    doc.setDrawColor(232, 237, 242)
    doc.line(MARGIN, y, PAGE_W - MARGIN, y)
    for (let i = 0; i < row.length; i++) {
      const v = row[i] === null || row[i] === undefined ? '' : String(row[i])
      doc.text(v, MARGIN + i * colW + pad, y + rowH - 2, { maxWidth: colW - pad * 2 })
    }
    y += rowH
  }
  return y + 6
}

function addChart(doc, chart, startY) {
  const maxW = PAGE_W - MARGIN * 2
  const maxH = 80
  const scale = Math.min(maxW / (chart.width || 1), maxH / (chart.height || 1))
  const cw = (chart.width || maxW) * scale
  const ch = (chart.height || maxH) * scale
  let y = startY
  if (y + ch > PAGE_H - 12) {
    doc.addPage()
    y = 16
  }
  doc.addImage(chart.url, 'PNG', MARGIN, y, cw, ch)
  return y + ch + 6
}

export function exportPDF(payload) {
  const doc = new jsPDF('p', 'mm', 'a4')
  let y = 16
  doc.setFontSize(16)
  doc.setTextColor(31, 41, 55)
  doc.text(payload.title || 'Report', MARGIN, y)
  y += 5
  doc.setFontSize(9)
  doc.setTextColor(120, 120, 120)
  doc.text('Generated: ' + new Date().toLocaleString(), MARGIN, y)
  y += 9

  doc.setFontSize(11)
  doc.setTextColor(31, 41, 55)
  for (const t of payload.tables) {
    if (y > PAGE_H - 30) {
      doc.addPage()
      y = 16
    }
    doc.text(String(t.name), MARGIN, y)
    y += 5
    y = drawTable(doc, t, y)
  }
  for (const ch of payload.charts || []) {
    if (y > PAGE_H - 30) {
      doc.addPage()
      y = 16
    }
    doc.text(String(ch.title), MARGIN, y)
    y += 5
    y = addChart(doc, ch, y)
  }
  doc.save(safeName(payload.title) + '.pdf')
}
