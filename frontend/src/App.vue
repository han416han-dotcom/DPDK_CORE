<template>
  <div id="app-container">
    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <div class="logo-area">
            <div class="logo-icon">
              <el-icon :size="20"><Cpu /></el-icon>
            </div>
            <div class="logo-text">
              <span class="app-title">DPDK Core Analyzer</span>
              <span class="app-subtitle">Core Dump 智能解析与诊断</span>
            </div>
          </div>
        </div>

        <div class="header-right">
          <el-menu mode="horizontal" :ellipsis="false" router>
            <el-menu-item index="/">
              <el-icon><Upload /></el-icon>
              <span>上传分析</span>
            </el-menu-item>
            <el-menu-item index="/tasks">
              <el-icon><Document /></el-icon>
              <span>任务历史</span>
            </el-menu-item>
          </el-menu>
        </div>
      </el-header>

      <el-main class="app-main">
        <div class="page-container">
          <router-view />
        </div>
      </el-main>

      <el-footer class="app-footer">
        <span>DPDK Core Analyzer v1.0</span>
        <span class="footer-divider">•</span>
        <span>Spring Boot 3 + Vue 3</span>
      </el-footer>
    </el-container>
  </div>
</template>

<script setup>
</script>

<style>
body {
  margin: 0;
  font-family: var(--app-font);
  background-color: var(--app-bg);
}

#app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  height: var(--app-header-height);
  padding: 0 26px;
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--app-header-bg);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(220, 213, 203, 0.82);
  box-shadow: 0 12px 28px rgba(159, 152, 173, 0.06);
}

.header-left {
  display: flex;
  align-items: center;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  color: var(--app-primary-dark);
  background:
    radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.95), transparent 35%),
    linear-gradient(145deg, rgba(125, 149, 200, 0.20), rgba(138, 172, 157, 0.18));
  border: 1px solid rgba(125, 149, 200, 0.14);
}

.logo-text {
  display: flex;
  flex-direction: column;
}

.app-title {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.01em;
  line-height: 1.3;
  color: var(--app-text);
}

.app-subtitle {
  font-size: 11px;
  line-height: 1.3;
  color: var(--app-text-muted);
}

.el-menu--horizontal {
  border-bottom: none !important;
  background: transparent !important;
}

.el-menu--horizontal .el-menu-item {
  height: 44px;
  line-height: 44px;
  margin-left: 8px;
  padding: 0 14px;
  border-radius: 999px;
  color: var(--app-text-secondary) !important;
  background: transparent !important;
  border-bottom: none !important;
  gap: 6px;
  transition: background 0.2s ease, color 0.2s ease, transform 0.18s ease;
}

.el-menu--horizontal .el-menu-item:hover {
  color: var(--app-text) !important;
  background: rgba(125, 149, 200, 0.10) !important;
}

.el-menu--horizontal .el-menu-item.is-active {
  color: var(--app-primary-dark) !important;
  background: rgba(125, 149, 200, 0.16) !important;
  border-bottom: none !important;
}

.app-main {
  flex: 1;
  padding: 26px 24px 30px;
  min-height: calc(100vh - 108px);
}

.page-container {
  max-width: 1240px;
  margin: 0 auto;
}

.app-footer {
  height: 40px !important;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 12px;
  color: var(--app-text-muted);
  background: rgba(255, 251, 247, 0.8);
  border-top: 1px solid rgba(227, 220, 211, 0.78);
}

.footer-divider {
  color: rgba(142, 152, 170, 0.8);
}

@media (max-width: 860px) {
  .app-header {
    height: auto;
    flex-direction: column;
    align-items: stretch;
    padding: 14px 16px;
  }

  .header-right {
    width: 100%;
  }

  .el-menu--horizontal {
    justify-content: space-between;
  }

  .el-menu--horizontal .el-menu-item {
    margin-left: 0;
    flex: 1;
    justify-content: center;
  }

  .app-main {
    padding: 20px 16px 24px;
  }
}
</style>
