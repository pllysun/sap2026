<template>
  <header class="page-header">
    <div>
      <span v-if="label" class="page-kicker">{{ label }}</span>
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="description" class="page-desc">{{ description }}</p>
    </div>
    <div v-if="$slots.default" class="page-header__actions"><slot /></div>
    <div v-else class="page-header__art" aria-hidden="true"><svg class="page-header__orbit" viewBox="0 0 200 160" fill="none"><path d="M8 112C-9 34 136-20 181 52S78 176 30 122"/><path d="M24 104C16 54 123 11 158 62S79 146 43 118" stroke-dasharray="3 6"/><path d="M33 27v12m-6-6h12m127 81v10m-5-5h10"/><circle cx="170" cy="35" r="3"/></svg><i></i><i></i><i></i></div>
    <div v-if="$slots.body" class="page-header__body"><slot name="body" /></div>
  </header>
</template>

<script setup>
defineProps({ title: String, description: String, label: { type: String, default: 'SAP OJ · 算法学习' } })
</script>

<style scoped>
.page-header__art{position:relative}.page-header__orbit{position:absolute;inset:-15px -20px;width:calc(100% + 40px);height:calc(100% + 30px);stroke:#b9bcdf;stroke-width:.8;pointer-events:none;opacity:.55}
</style>
