<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { login, setAuthName, setAuthUserId, setToken } from "../api";

const route = useRoute();
const router = useRouter();

const form = reactive({
  username: "",
  password: ""
});

const busy = ref(false);
const error = ref("");
const success = ref("");

onMounted(() => {
  document.title = "NCF音乐推荐系统 - 登录";

  if (typeof route.query.username === "string") {
    form.username = route.query.username;
  }
  if (route.query.registered === "1") {
    success.value = "注册成功，请使用新账号登录。";
  }
});

const submitLogin = async () => {
  busy.value = true;
  error.value = "";
  success.value = "";

  try {
    const result = await login({
      username: form.username,
      password: form.password
    });
    setToken(`${result.tokenType} ${result.token}`);
    setAuthName(result.username || form.username || (result.userId ? String(result.userId) : "已登录用户"));
    setAuthUserId(result.userId || "");

    const redirect =
      typeof route.query.redirect === "string" ? route.query.redirect : null;
    if (redirect) {
      router.replace(redirect);
    } else {
      router.replace({ name: "home-overview" });
    }
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};
</script>

<template>
  <div class="auth-page">
    <h1 class="auth-page-title">NCF音乐推荐系统</h1>
    <section class="auth-card auth-card--compact">
      <div class="auth-header">
        <h1>用户登录</h1>
        <p class="auth-note">使用系统账号和密码登录音乐推荐平台。</p>
      </div>

      <form class="auth-form" @submit.prevent="submitLogin">
        <label class="auth-field" for="login-username">
          <span class="field-label">用户名</span>
          <input id="login-username" v-model.trim="form.username" autocomplete="username" maxlength="32"
            placeholder="请输入用户名" />
        </label>

        <label class="auth-field" for="login-password">
          <span class="field-label">密码</span>
          <input id="login-password" v-model="form.password" autocomplete="current-password" maxlength="64"
            placeholder="请输入密码" type="password" />
        </label>

        <div class="auth-actions">
          <button :disabled="busy" class="btn-primary auth-submit" type="submit">
            <span v-if="busy">登录中...</span>
            <span v-else>登录</span>
          </button>
        </div>

        <p v-if="success" class="success-text">{{ success }}</p>
        <p v-if="error" class="error-text">{{ error }}</p>
      </form>

      <p class="switch-text">
        还没有账号？
        <RouterLink class="text-link" to="/register">立即注册</RouterLink>
      </p>
    </section>
  </div>
</template>
