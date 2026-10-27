import { reactive } from 'vue'

export const exportStore = reactive({ handlers: {} })

export function registerExporter(name, handler) {
  exportStore.handlers[name] = handler
}
