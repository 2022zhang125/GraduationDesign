<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { register } from "../api";

const router = useRouter();

const genderOptions = [
  { value: 0, label: "保密" },
  { value: 1, label: "男" },
  { value: 2, label: "女" }
];

const form = reactive({
  username: "",
  password: "",
  gender: 1,
  birthYear: 1998
});

const busy = ref(false);
const error = ref("");

const submitRegister = async () => {
  busy.value = true;
  error.value = "";

  try {
    const result = await register({
      externalUserNo: form.username,
      password: form.password,
      gender: Number(form.gender),
      birthYear: Number(form.birthYear)
    });

    router.push({
      name: "login",
      query: {
        username: result.username,
        registered: "1"
      }
    });
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};
</script>

<template>
  <div class="auth-page">
    <section class="auth-card auth-card--wide">
      <div class="auth-header">
        <h1>注册账号</h1>
        <p class="auth-note">密码会以 BCrypt 哈希形式保存到数据库中。</p>
      </div>

      <form class="auth-form" @submit.prevent="submitRegister">
        <label class="auth-field" for="register-username">
          <span class="field-label">用户名</span>
          <input
            id="register-username"
            v-model.trim="form.username"
            autocomplete="username"
            maxlength="32"
            placeholder="请输入 4-32 位用户名"
          />
        </label>

        <label class="auth-field" for="register-password">
          <span class="field-label">密码</span>
          <input
            id="register-password"
            v-model="form.password"
            autocomplete="new-password"
            maxlength="64"
            placeholder="请输入至少 6 位密码"
            type="password"
          />
        </label>

        <div class="auth-grid">
          <fieldset class="gender-field">
            <legend class="field-label">性别</legend>
            <div class="gender-options">
              <button
                v-for="option in genderOptions"
                :key="option.value"
                :class="['gender-option', { 'is-active': Number(form.gender) === option.value }]"
                type="button"
                @click="form.gender = option.value"
              >
                {{ option.label }}
              </button>
            </div>
          </fieldset>

          <label class="auth-field" for="register-birth-year">
            <span class="field-label">出生年份</span>
            <input
              id="register-birth-year"
              v-model.number="form.birthYear"
              max="2100"
              min="1900"
              type="number"
            />
          </label>
        </div>

        <div class="auth-actions">
          <button :disabled="busy" class="btn-primary auth-submit" type="submit">
            {{ busy ? "注册中..." : "注册" }}
          </button>
        </div>

        <p v-if="error" class="error-text">{{ error }}</p>
      </form>

      <p class="switch-text">
        已有账号？
        <RouterLink class="text-link" to="/login">返回登录</RouterLink>
      </p>
    </section>
  </div>
</template>
