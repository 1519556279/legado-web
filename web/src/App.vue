<script setup lang="ts">
import { computed } from 'vue'
import {
  NConfigProvider, NDialogProvider, NMessageProvider, NNotificationProvider, NLoadingBarProvider,
  darkTheme, zhCN, dateZhCN, zhTW, dateZhTW, enUS, dateEnUS, type GlobalThemeOverrides,
} from 'naive-ui'
import { locale } from '@/i18n'
import { useUiStore } from '@/stores/ui'
import { useRouter } from 'vue-router'
import { onUnauthorized } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import AppShellHelper from '@/components/AppShellHelper.vue'
import SourceBrowserBridge from '@/components/SourceBrowserBridge.vue'

const ui = useUiStore()
const router = useRouter()
const auth = useAuthStore()

onUnauthorized(() => {
  auth.logout()
  if (router.currentRoute.value.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  }
})

// naive-ui 组件自带文字（日期选择、分页、空状态等）的语言
const naiveLocale = locale === 'en' ? enUS : locale === 'zh-TW' ? zhTW : zhCN
const naiveDateLocale = locale === 'en' ? dateEnUS : locale === 'zh-TW' ? dateZhTW : dateZhCN

const overrides = computed<GlobalThemeOverrides>(() => {
  const primary = ui.isDark ? '#5fb8a5' : '#2f7d6d'
  return {
    common: {
      primaryColor: primary,
      primaryColorHover: ui.isDark ? '#74cbb8' : '#3a9783',
      primaryColorPressed: ui.isDark ? '#4ba08e' : '#25695b',
      primaryColorSuppl: primary,
      borderRadius: '8px',
      fontFamily:
        '-apple-system, BlinkMacSystemFont, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Noto Sans SC", sans-serif',
    },
  }
})
</script>

<template>
  <n-config-provider
    :theme="ui.isDark ? darkTheme : null"
    :theme-overrides="overrides"
    :locale="naiveLocale"
    :date-locale="naiveDateLocale"
  >
    <n-loading-bar-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <n-message-provider>
            <router-view v-slot="{ Component }">
              <transition name="fade-slide" mode="out-in">
                <component :is="Component" />
              </transition>
            </router-view>
            <app-shell-helper />
            <source-browser-bridge v-if="auth.loggedIn" />
          </n-message-provider>
        </n-notification-provider>
      </n-dialog-provider>
    </n-loading-bar-provider>
  </n-config-provider>
</template>

<style>
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
