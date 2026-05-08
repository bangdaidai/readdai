<template>
  <el-input
    v-if="isBookSource"
    id="debug-key"
    v-model="searchKey"
    placeholder="搜索书名、作者"
    :prefix-icon="Search"
    style="padding-bottom: 4px"
    @keydown.enter="startDebug"
  />
  <el-input
    id="debug-text"
    v-model="printDebug"
    type="textarea"
    readonly
    :rows="29"
    placeholder="这里用于输出调试信息"
  />
</template>

<script setup lang="ts">
import API from '@api'
import { Search } from '@element-plus/icons-vue'
import { useBookStore } from '@/store'

const store = useSourceStore()
const bookStore = useBookStore()

const printDebug = ref('')
const searchKey = ref('')

watch(
  () => store.isDebuging,
  () => {
    if (store.isDebuging) startDebug()
  },
)

watch(
  () => bookStore.isNight,
  (isNight) => {
    if (isNight) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  },
  { immediate: true }
)

const appendDebugMsg = (msg: string) => {
  const debugDom = document.querySelector('#debug-text')
  debugDom!.scrollTop = debugDom!.scrollHeight
  printDebug.value += msg + '\n'
}
const startDebug = async () => {
  printDebug.value = ''
  try {
    await API.saveSource(store.currentSource)
  } catch (e) {
    store.debugFinish()
    throw e
  }
  API.debug(
    store.currentSourceUrl,
    searchKey.value || store.searchKey,
    appendDebugMsg,
    store.debugFinish,
  )
}

const isBookSource = computed(() => {
  return /bookSource/i.test(window.location.href)
})
</script>

<style lang="scss" scoped>
:deep(#debug-text) {
  height: calc(100vh - 45px - 36px - 5px);
}

:deep(.el-input__inner) {
  color: var(--el-text-color-primary) !important;
  background-color: var(--el-bg-color) !important;
  border-color: var(--el-border-color) !important;
}

:deep(.el-input__prefix-icon) {
  color: var(--el-text-color-primary) !important;
}

:deep(.el-input__placeholder) {
  color: var(--el-text-color-placeholder) !important;
}

:deep(.el-input) {
  --el-input-bg-color: var(--el-bg-color) !important;
  --el-input-text-color: var(--el-text-color-primary) !important;
  --el-input-placeholder-color: var(--el-text-color-placeholder) !important;
  --el-input-border-color: var(--el-border-color) !important;
}
</style>
