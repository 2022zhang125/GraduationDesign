<script setup>
import { computed } from "vue";
import { RouterLink, RouterView } from "vue-router";
import GlobalAudioPlayer from "../components/GlobalAudioPlayer.vue";
import { resolveCurrentUserId, resolveCurrentUsername, isAdminUser } from "../utils/session";

const text = {
  defaultUser: "已登录用户",
  admin: "管理员",
  user: "用户",
  brand: "NCF 音乐推荐系统",
  logout: "退出登录",
  footer: "2026 NCF 音乐推荐系统"
};

const username = computed(() => resolveCurrentUsername() || text.defaultUser);
const currentUserId = computed(() => resolveCurrentUserId());
const admin = computed(() => isAdminUser(username.value));
const titleLabel = computed(() =>
  `${admin.value ? text.admin : text.user}：${username.value}`
);

const navItems = computed(() => {
  if (admin.value) {
    return [
      { name: "home-overview", label: "控制台", to: { name: "home-overview" } },
      { name: "home-library", label: "曲库管理", to: { name: "home-library" } },
      { name: "home-search", label: "歌曲搜索", to: { name: "home-search" } },
      { name: "home-recommendations", label: "推荐查看", to: { name: "home-recommendations" } },
      { name: "home-favorites", label: "收藏查看", to: { name: "home-favorites" } },
      { name: "home-history", label: "听歌历史", to: { name: "home-history" } },
      { name: "home-friends", label: "好友关系", to: { name: "home-friends" } },
      { name: "home-feedback", label: "用户反馈", to: { name: "home-feedback" } }
    ];
  }

  const userId = currentUserId.value;
  return [
    { name: "home-overview", label: "我的主页", to: { name: "home-overview" } },
    { name: "home-recommendations", label: "每日推荐", to: { name: "home-recommendations", params: { userId } } },
    { name: "home-favorites", label: "我的收藏", to: { name: "home-favorites", params: { userId } } },
    { name: "home-history", label: "听歌历史", to: { name: "home-history", params: { userId } } },
    { name: "home-friends", label: "好友关注", to: { name: "home-friends", params: { userId } } },
    { name: "home-feedback", label: "我的反馈", to: { name: "home-feedback", params: { userId } } },
    { name: "home-search", label: "搜索歌曲", to: { name: "home-search", params: { userId } } }
  ];
});
</script>

<template>
  <div class="home-shell">
    <header class="top-nav">
      <div class="brand">{{ text.brand }}</div>
      <nav class="menu">
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          :to="item.to"
          class="menu-link"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
      <div class="token-zone">
        <span class="token-text" :title="username">{{ titleLabel }}</span>
        <RouterLink class="logout-link" to="/logout">{{ text.logout }}</RouterLink>
      </div>
    </header>

    <main class="content-wrap">
      <section class="content-card">
        <RouterView />
      </section>
    </main>

    <GlobalAudioPlayer />

    <footer class="footer">{{ text.footer }}</footer>
  </div>
</template>
