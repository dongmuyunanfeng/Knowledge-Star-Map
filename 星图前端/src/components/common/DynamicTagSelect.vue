<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :multiple="multiple"
    filterable
    clearable
    allow-create
    default-first-option
    :reserve-keyword="false"
    @change="handleChange"
    @create="handleCreate"
    style="width: 100%"
  >
    <el-option
      v-for="tag in filteredTags"
      :key="tag"
      :label="tag"
      :value="tag"
    />
    <el-option
      v-if="inputValue && !filteredTags.includes(inputValue) && !loading"
      :label="`+ 新增：${inputValue}`"
      :value="inputValue"
      class="create-option"
    />
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'

interface Props {
  modelValue?: string | string[]
  placeholder?: string
  multiple?: boolean
  fetchTags?: () => Promise<string[]>
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '请选择或输入标签',
  multiple: false,
  fetchTags: () => import('@/api').then(m => m.domainApi.list()).then(r => r.data?.domainNames || [])
})

const emit = defineEmits<{
  (e: 'change', value: string | string[]): void
  (e: 'update:modelValue', value: string | string[]): void
}>()

const tags = ref<string[]>([])
const inputValue = ref('')
const loading = ref(false)

async function fetchTags() {
  loading.value = true
  try {
    const res: any = await props.fetchTags()
    const data = res?.data
    if (Array.isArray(data)) {
      tags.value = data
    } else if (data && typeof data === 'object') {
      tags.value = data.domainNames || data.tags || []
    }
  } finally {
    loading.value = false
  }
}

onMounted(fetchTags)

const filteredTags = computed(() => {
  if (!inputValue.value) return tags.value
  return tags.value.filter((t) => t.includes(inputValue.value))
})

function handleChange(value: string | string[]) {
  emit('change', value)
  emit('update:modelValue', value)
}

async function handleCreate(val: string) {
  if (!val || tags.value.includes(val)) return
  loading.value = true
  try {
    await props.fetchTags()
    if (!tags.value.includes(val)) tags.value.push(val)
    if (props.multiple) {
      const arr = Array.isArray(props.modelValue) ? [...props.modelValue] : []
      if (!arr.includes(val)) arr.push(val)
      handleChange(arr)
    } else {
      handleChange(val)
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.create-option {
  color: #409eff;
}
</style>
