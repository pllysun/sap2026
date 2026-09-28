<template><span class="user-avatar" :style="{ width: `${size}px`, height: `${size}px` }"><img v-if="src && !failed" :src="src" alt="" @error="failed = true"><span v-else aria-hidden="true">{{ (name?.trim() || '协')[0] }}</span></span></template>
<script setup>
import { ref, watch } from 'vue'
const props = defineProps({ src: String, name: String, size: { type: Number, default: 32 } })
const failed = ref(false)
watch(() => props.src, () => { failed.value = false })
</script>
<style scoped>
.user-avatar{display:inline-grid;place-items:center;border-radius:50%;background:var(--surface-blue);color:var(--primary-dark);font-size:13px;font-weight:600;flex-shrink:0;overflow:hidden;vertical-align:middle}.user-avatar img{width:100%;height:100%;object-fit:cover}
</style>
