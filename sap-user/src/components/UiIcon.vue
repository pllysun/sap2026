<template>
  <svg
    class="ui-icon"
    viewBox="0 0 24 24"
    :width="size"
    :height="size"
    fill="none"
    stroke="currentColor"
    stroke-width="1.65"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="(path, index) in paths[name]" :key="index" :d="path" />
  </svg>
</template>

<script setup>
defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 18 },
})

const paths = {
  'credit-card': ['M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z', 'M2 10h20M6 15h3'],
  'refresh-cw': ['M20 7v5h-5', 'M4 17v-5h5', 'M6.1 6.1A8 8 0 0 1 19.9 12M4.1 12A8 8 0 0 0 17.9 17.9'],
  eye: ['M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z', 'M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z'],
  trash: ['M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7'],
  'log-out': ['M9 21H4V3h5M9 12h12m-5-5 5 5-5 5'],
  route: ['M5 5a2 2 0 1 1-4 0 2 2 0 0 1 4 0ZM23 19a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z', 'M5 5h10a4 4 0 0 1 0 8H9a3 3 0 0 0 0 6h10'],
  star: ['m12 3 2.8 5.7 6.3.9-4.5 4.4 1.1 6.2-5.7-3-5.7 3 1.1-6.2L3.2 9.6l6.3-.9Z'],
  bookmark: ['M6 3h12v18l-6-4-6 4Z'],
  target: ['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0ZM13 12a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z'],
  filter: ['M4 6h16M7 12h10M10 18h4', 'M7 4v4M16 10v4M12 16v4'],
  copy: ['M9 9h12v12H9Z', 'M15 9V3H3v12h6'],
  edit: ['m16 3 5 5-12 12-6 1 1-6Z', 'm13 6 5 5'],
  send: ['m22 2-7 20-4-9-9-4Z', 'M22 2 11 13'],
  info: ['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z', 'M12 11v6M12 7h.01'],
  users: ['M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2', 'M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z', 'M17 3a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-3.87'],
  'user-plus': ['M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2', 'M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z', 'M20 8v6M17 11h6'],
  'book-open': ['M12 6v15', 'M12 6C9 3 5 3 2 4v15c3-1 7-1 10 2 3-3 7-3 10-2V4c-3-1-7-1-10 2Z'],
  calendar: ['M5 5h14a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z', 'M16 2v6M8 2v6M3 11h18M8 15h2M14 15h2M8 18h2'],
  'message-square': ['M5 4h14a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H9l-6 3V6a2 2 0 0 1 2-2Z', 'M7 9h10M7 13h6'],
  image: ['M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z', 'M9.5 8a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0ZM21 15l-5-5L5 21'],
  'file-save': ['M6 14V4a2 2 0 0 1 2-2h6l4 4v8M14 2v4h4M9 9h6M9 12h4', 'M3 14h5l2 3h4l2-3h5v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z'],
  'file-plus': ['M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z', 'M14 2v6h6M12 12v6M9 15h6'],
  'clipboard-check': ['M9 4H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-3', 'M9 2h6v4H9ZM8 14l3 3 5-6'],
  trophy: ['M8 3h8v6a4 4 0 0 1-8 0Z', 'M8 5H4v2a4 4 0 0 0 4 4M16 5h4v2a4 4 0 0 1-4 4M12 13v5M8 21v-3h8v3M6 21h12'],
  link: ['M10 13a5 5 0 0 0 7 .5l3-3a5 5 0 0 0-7-7l-2 2', 'M14 11a5 5 0 0 0-7-.5l-3 3a5 5 0 0 0 7 7l2-2'],
  list: ['M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01'],
  'panel-top': ['M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2ZM3 8h18M7 5.5h.01M10 5.5h.01'],
  layers: ['M12 3 2 8l10 5 10-5Z', 'm2 12 10 5 10-5M2 16l10 5 10-5'],
  search: ['M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z', 'm15 15 6 6'],
  'chevron-left': ['m15 18-6-6 6-6'],
  'arrow-right': ['M4 12h16m-6-6 6 6-6 6'],
  'chevron-right': ['m9 18 6-6-6-6'],
  'chevron-down': ['m6 9 6 6 6-6'],
  'chevron-up': ['m6 15 6-6 6 6'],
  close: ['m6 6 12 12M18 6 6 18'],
  play: ['m8 4 12 8-12 8Z'],
  history: ['M3 11a9 9 0 1 1 2.6 7M3 4v7h7', 'M12 7v5l3 2'],
  chart: ['M4 3v17h17M8 16v-5M13 16V7M18 16v-8'],
  check: ['m5 12 4 4L19 6'],
  'shield-check': ['M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7Z', 'm8 12 3 3 5-6'],
  lock: ['M6 10h12a2 2 0 0 1 2 2v8H4v-8a2 2 0 0 1 2-2Z', 'M8 10V6a4 4 0 0 1 8 0v4M12 14v3'],
  clock: ['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z', 'M12 7v5l3 2'],
  cpu: ['M7 7h10v10H7ZM10 10h4v4h-4ZM9 3v4M15 3v4M9 17v4M15 17v4M3 9h4M3 15h4M17 9h4M17 15h4'],
  code: ['m8 7-5 5 5 5M16 7l5 5-5 5M14 4l-4 16'],
  refresh: ['M20 7a8 8 0 0 0-14-2L3 8M3 3v5h5M4 17a8 8 0 0 0 14 2l3-3M16 16h5v5'],
  warning: ['m12 3 10 18H2ZM12 9v5M12 17h.01'],
  terminal: ['m5 7 5 5-5 5M13 17h6'],
}
</script>

<style scoped>
.ui-icon {
  display: inline-block;
  flex-shrink: 0;
  vertical-align: middle;
}
</style>
