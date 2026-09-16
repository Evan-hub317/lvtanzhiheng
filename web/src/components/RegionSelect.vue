<template>
  <div class="region-select">
    <el-select
      :model-value="provinceId"
      placeholder="选择省份"
      :style="{ width: width + 'px' }"
      @update:model-value="onProvinceChange"
    >
      <el-option label="🌏 全国" :value="1" />
      <el-option v-for="p in provinces" :key="p.id" :label="p.regionName" :value="p.id" />
    </el-select>
    <el-select
      v-if="showCitySelect"
      :model-value="modelValue"
      placeholder="全部地市"
      :style="{ width: width + 'px' }"
      @update:model-value="onCityChange"
    >
      <el-option label="全省" :value="provinceId" />
      <el-option v-for="c in cities" :key="c.id" :label="c.regionName" :value="c.id" />
    </el-select>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { regionListApi } from '@/api/dict'

/**
 * 区域选择组件（全国 / 省 / 市 三级视角）
 * v-model 绑定区域ID：1=全国，省ID=全省口径，市ID=该市
 */
const props = defineProps({
  modelValue: { type: Number, default: 1 },
  width: { type: Number, default: 130 }
})
const emit = defineEmits(['update:modelValue', 'change'])

const allRegions = ref([])
const provinces = computed(() => allRegions.value.filter(r => r.level === 1))

// 当前选中值反推省份：1=全国；省=自身；市=上级省
const provinceId = computed(() => {
  if (props.modelValue === 1) return 1
  const current = allRegions.value.find(r => r.id === props.modelValue)
  if (!current) return null
  return current.level === 1 ? current.id : current.parentId
})

const cities = computed(() =>
  allRegions.value.filter(r => r.level === 2 && r.parentId === provinceId.value)
)

const showCitySelect = computed(() => provinceId.value && provinceId.value !== 1)

function onProvinceChange(p) {
  emit('update:modelValue', p)
  emit('change')
}

function onCityChange(c) {
  emit('update:modelValue', c)
  emit('change')
}

onMounted(async () => {
  const res = await regionListApi()
  allRegions.value = res.data.filter(r => r.level <= 2)
})
</script>

<style scoped>
.region-select {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
