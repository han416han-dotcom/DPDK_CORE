<template>
  <div class="stats-card" :style="{ '--accent-color': color }">
    <div class="stats-card-accent"></div>
    <div class="stats-card-body">
      <div class="stats-card-icon">
        <el-icon :size="24"><component :is="icon" /></el-icon>
      </div>
      <div class="stats-card-info">
        <span class="stats-card-label">{{ label }}</span>
        <span class="stats-card-value" v-if="!loading">{{ value }}</span>
        <span class="stats-card-subtitle" v-if="subtitle">{{ subtitle }}</span>
        <el-skeleton :rows="1" animated v-if="loading" style="width: 60%; margin-top: 4px" />
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  icon: { type: [String, Object], default: 'InfoFilled' },
  label: { type: String, default: '' },
  value: { type: [String, Number], default: '' },
  subtitle: { type: String, default: '' },
  color: { type: String, default: '#1a6eff' },
  loading: { type: Boolean, default: false },
})
</script>

<style scoped>
.stats-card {
  display: flex;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  overflow: hidden;
  transition: box-shadow 0.2s, transform 0.2s;
}

.stats-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

.stats-card-accent {
  width: 4px;
  background: var(--accent-color);
  flex-shrink: 0;
}

.stats-card-body {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
}

.stats-card-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: color-mix(in srgb, var(--accent-color) 10%, transparent);
  color: var(--accent-color);
  flex-shrink: 0;
}

.stats-card-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.stats-card-label {
  font-size: 13px;
  color: #909399;
  line-height: 1.3;
}

.stats-card-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  line-height: 1.3;
}

.stats-card-subtitle {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
</style>
