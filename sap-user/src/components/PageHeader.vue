<template>
  <header class="page-header">
    <div>
      <span class="page-kicker">{{ label }}</span>
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="description" class="page-desc">{{ description }}</p>
    </div>
    <div v-if="$slots.default" class="page-header__actions"><slot /></div>
    <div v-else class="page-header__art" aria-hidden="true"><i></i><i></i><i></i></div>
  </header>
</template>

<script setup>
defineProps({ title: String, description: String, label: { type: String, default: 'CSUFT · 软件协会' } })
</script>
