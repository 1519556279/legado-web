<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NForm, NFormItem, NInput, NAlert, useMessage } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import CapCaptcha from '@/components/CapCaptcha.vue'
import { LOCALES, locale, setLocale, t } from '@/i18n'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()

const tab = ref<'login' | 'register'>('login')
const loading = ref(false)
const form = reactive({ username: '', password: '', password2: '', inviteCode: '' })

const mode = computed(() => auth.config?.registrationMode ?? 'invite')
const firstUser = computed(() => auth.config && !auth.config.hasUsers)
const canRegister = computed(() => firstUser.value || mode.value !== 'closed')
const needInvite = computed(() => !firstUser.value && mode.value === 'invite')
const captcha = computed(() => auth.config?.captcha ?? null)
const captchaToken = ref('')
const capRef = ref<InstanceType<typeof CapCaptcha>>()

onMounted(async () => {
  try {
    await auth.loadConfig()
    if (firstUser.value) tab.value = 'register'
    const code = route.query.invite as string | undefined
    if (code) {
      form.inviteCode = code
      tab.value = 'register'
    }
  } catch (e: any) {
    message.error(e.message)
  }
})

async function submit() {
  if (!form.username || !form.password) return message.warning(t('请输入用户名和密码'))
  if (captcha.value && !captchaToken.value) return message.warning(t('请先完成人机验证'))
  loading.value = true
  try {
    if (tab.value === 'login') {
      await auth.login(form.username, form.password, captchaToken.value || undefined)
    } else {
      if (form.password !== form.password2) {
        message.warning(t('两次输入的密码不一致'))
        return
      }
      await auth.register(form.username, form.password, form.inviteCode || undefined, captchaToken.value || undefined)
      message.success(firstUser.value ? t('管理员账号已创建') : t('注册成功'))
    }
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch (e: any) {
    message.error(e.message)
    // 验证码 token 只能用一次，失败后要重新验证
    capRef.value?.reset()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card card">
      <div class="logo">
        <img src="/favicon.svg" alt="" />
        <h1>{{ auth.siteName }}</h1>
        <p class="muted">{{ t('自建书源 · 多端同步的网页阅读器') }}</p>
      </div>
      <n-alert v-if="firstUser" type="info" :bordered="false" style="margin-bottom: 16px">
        {{ t('系统还没有用户，第一个注册的账号将成为管理员。') }}
      </n-alert>
      <div class="mode-switch" role="tablist">
        <button
          type="button"
          role="tab"
          class="mode-item"
          :class="{ active: tab === 'login' }"
          :disabled="!!firstUser"
          @click="tab = 'login'"
        >
          {{ t('登录') }}
        </button>
        <button
          v-if="canRegister"
          type="button"
          role="tab"
          class="mode-item"
          :class="{ active: tab === 'register' }"
          @click="tab = 'register'"
        >
          {{ t('注册') }}
        </button>
      </div>
      <n-form style="margin-top: 16px" @submit.prevent="submit">
        <n-form-item :label="t('用户名')" :show-feedback="false" style="margin-bottom: 14px">
          <n-input v-model:value="form.username" :placeholder="t('用户名')" autocomplete="username" @keyup.enter="submit" />
        </n-form-item>
        <n-form-item :label="t('密码')" :show-feedback="false" style="margin-bottom: 14px">
          <n-input
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            :placeholder="t('密码（至少 6 位）')"
            :autocomplete="tab === 'login' ? 'current-password' : 'new-password'"
            @keyup.enter="submit"
          />
        </n-form-item>
        <template v-if="tab === 'register'">
          <n-form-item :label="t('确认密码')" :show-feedback="false" style="margin-bottom: 14px">
            <n-input v-model:value="form.password2" type="password" :placeholder="t('再次输入密码')" autocomplete="new-password" />
          </n-form-item>
          <n-form-item v-if="needInvite" :label="t('邀请码')" :show-feedback="false" style="margin-bottom: 14px">
            <n-input v-model:value="form.inviteCode" :placeholder="t('请输入邀请码')" />
          </n-form-item>
        </template>
        <n-form-item v-if="captcha" :show-feedback="false" style="margin-bottom: 14px">
          <CapCaptcha ref="capRef" v-model="captchaToken" :endpoint="captcha.endpoint" @error="(m: string) => message.error(m)" />
        </n-form-item>
        <n-button type="primary" block :loading="loading" attr-type="submit" size="large">
          {{ tab === 'login' ? t('登录') : firstUser ? t('创建管理员') : t('注册') }}
        </n-button>
      </n-form>
      <p v-if="!canRegister && tab === 'login'" class="muted hint">{{ t('系统已关闭注册，请联系管理员。') }}</p>
      <div class="langs">
        <button v-for="l in LOCALES" :key="l.value" type="button" :class="{ on: l.value === locale }" @click="l.value !== locale && setLocale(l.value)">
          {{ l.label }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  position: relative;
  overflow: hidden;
  background: var(--app-bg);
}

.login-page::before,
.login-page::after {
  content: '';
  position: absolute;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  opacity: 0.55;
  pointer-events: none;
  animation: float-glow 12s ease-in-out infinite alternate;
}

.login-page::before {
  top: -160px;
  left: -120px;
  background: radial-gradient(circle, color-mix(in srgb, var(--app-accent) 20%, transparent), transparent 65%);
}

.login-page::after {
  bottom: -180px;
  right: -140px;
  background: radial-gradient(circle, color-mix(in srgb, var(--app-accent) 14%, transparent), transparent 65%);
  animation-delay: -6s;
}

@keyframes float-glow {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(24px, 18px, 0) scale(1.08);
  }
}

.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 380px;
  padding: 28px 24px;
  animation: card-in 0.45s cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* 登录 / 注册切换 */
.mode-switch {
  position: relative;
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 1fr;
  gap: 4px;
  padding: 4px;
  margin-bottom: 18px;
  border-radius: 12px;
  background: var(--app-accent-soft);
}

.mode-item {
  position: relative;
  z-index: 1;
  border: 0;
  background: transparent;
  padding: 8px 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--app-text-2);
  border-radius: 9px;
  cursor: pointer;
  transition: color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.mode-item.active {
  color: var(--app-accent);
  background: var(--app-surface);
  box-shadow: 0 1px 4px rgba(24, 48, 44, 0.12);
}

.mode-item:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.logo {
  text-align: center;
  margin-bottom: 20px;
}

.logo img {
  width: 52px;
  height: 52px;
}

.logo h1 {
  margin: 10px 0 4px;
  font-size: 22px;
}

.logo p {
  margin: 0;
  font-size: 13px;
}

.hint {
  text-align: center;
  font-size: 13px;
  margin: 14px 0 0;
}

.langs {
  display: flex;
  justify-content: center;
  gap: 4px;
  margin-top: 18px;
}

.langs button {
  border: 0;
  background: none;
  padding: 2px 8px;
  font-size: 12px;
  color: var(--app-text-2);
  cursor: pointer;
  border-radius: 4px;
  transition: color 0.18s ease, background-color 0.18s ease;
}

.langs button:hover {
  background: var(--app-accent-soft);
}

.langs button.on {
  color: var(--app-accent);
  font-weight: 600;
}
</style>
