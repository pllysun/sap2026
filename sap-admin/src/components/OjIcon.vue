<template>
  <svg class="oj-icon" viewBox="0 0 24 24" :width="size" :height="size" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path v-for="(path,index) in icons[name] || icons.code" :key="index" :d="path" /></svg>
</template>
<script setup>
defineProps({name:{type:String,required:true},size:{type:[Number,String],default:17}})
const icons={
  search:['M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z','m15 15 6 6'],
  plus:['M12 5v14M5 12h14'],
  edit:['m15 4 5 5M4 20l5-1L21 7a2 2 0 0 0-5-5L4 14Z'],
  cases:['M9 4H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-3M9 2h6v4H9ZM8 12l2 2 3-3M8 18h8'],
  check:['m5 12 4 4L19 6'],
  'circle-check':['M21 12a9 9 0 1 1-2.6-6.4','m8 11 3 3 10-10'],
  refresh:['M20 7v5h-5M4 17v-5h5','M5.2 7a8 8 0 0 1 13-2L20 8M4 16l1.8 3a8 8 0 0 0 13-2'],
  save:['M5 3h12l4 4v13a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a2 2 0 0 1 2-2ZM7 3v6h9V3M7 21v-7h10v7'],
  upload:['M12 16V3m-5 5 5-5 5 5M4 16v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4'],
  download:['M12 3v13m-5-5 5 5 5-5M4 16v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4'],
  sort:['M8 3v18m-4-4 4 4 4-4M16 21V3m-4 4 4-4 4 4'],
  'arrow-up':['M12 20V4m-6 6 6-6 6 6'],
  'arrow-down':['M12 4v16m-6-6 6 6 6-6'],
  'chevron-right':['m9 5 7 7-7 7'],
  'chevron-down':['m5 9 7 7 7-7'],
  close:['m6 6 12 12M18 6 6 18'],
  more:['M5 12h.01M12 12h.01M19 12h.01'],
  code:['m8 7-5 5 5 5M16 7l5 5-5 5M14 3l-4 18'],
  terminal:['M4 4h16a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z','m7 8 4 4-4 4M13 16h4'],
  timeline:['M5 3v18M3 5h4M3 12h4M3 19h4M11 5h10M11 12h7M11 19h10'],
  history:['M3 5v5h5M3.5 9a9 9 0 1 1 1 9M12 7v5l3 2'],
  queue:['M8 5h13M8 12h13M8 19h13M3 4v2M3 11v2M3 18v2'],
  stats:['M4 3v17a1 1 0 0 0 1 1h16M8 16v-6M13 16V5M18 16v-9'],
  format:['M4 5h16M7 9h10M7 13h6M4 17h9M17 13v8m-3-3 3 3 3-3'],
  file:['M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8ZM14 2v6h6M8 13h8M8 17h5'],
  copy:['M9 9h11v12H9ZM16 5V2H3v15h3'],
  trash:['M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7'],
  shield:['M12 2 3 6v6c0 5 5 8 9 10 4-2 9-5 9-10V6ZM8 12l3 3 5-6'],
  ban:['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM6 6l12 12'],
  eye:['M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7ZM15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z'],
  clock:['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 7v5l3 2'],
  server:['M5 3h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2ZM5 14h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2ZM7 6.5h.01M7 17.5h.01M12 6.5h5M12 17.5h5'],
  power:['M12 2v10M6 5a9 9 0 1 0 12 0'],
  settings:['M4 7h16M4 17h16M8 4v6M16 14v6'],
  library:['M4 3h4v18H4ZM10 3h4v18h-4ZM16 4l4-1 4 17-4 1Z'],
  language:['M3 5h12M9 2v3M5 5c0 6 4 9 8 11M13 5c0 6-4 9-10 11M14 21l4-10 4 10M16 17h4'],
  connection:['M9 13a5 5 0 0 0 7 0l4-4a5 5 0 0 0-7-7l-2 2M15 11a5 5 0 0 0-7 0l-4 4a5 5 0 0 0 7 7l2-2'],
  alert:['m12 3 10 18H2ZM12 9v5m0 3h.01'],
  play:['m8 4 13 8-13 8Z'],
}
</script>
<style scoped>
.oj-icon{display:inline-block;vertical-align:middle;flex-shrink:0}
</style>
