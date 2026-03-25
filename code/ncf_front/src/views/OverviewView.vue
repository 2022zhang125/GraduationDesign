<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  fetchFavoriteItems,
  fetchFollowingUsers,
  fetchListeningHistory,
  fetchRecommendations,
  fetchStats
} from "../api";
import { isAdminUser, resolveCurrentUserId, resolveCurrentUsername } from "../utils/session";

const text = {
  adminTitle: "管理总览",
  userTitle: "我的音乐主页",
  refresh: "刷新数据",
  missingUser: "未获取到当前用户信息，请重新登录后再试。",
  adminQuick: "管理员快捷操作",
  inputUserKey: "输入用户 ID 或用户名",
  checkRecommendations: "查看推荐",
  checkHistory: "听歌历史",
  checkFriends: "好友关系",
  goLibrary: "进入曲库管理",
  welcome: "欢迎回来",
  explorer: "音乐探索者",
  heroCopy: "这里会集中展示你的每日推荐、收藏歌曲、听歌历史和好友关注。关注喜欢的好友后，系统会把对方的收藏与听歌历史作为社交信号纳入推荐计算。",
  openRecommendations: "查看每日推荐",
  openFriends: "管理好友关注",
  openSearch: "搜索歌曲",
  statRecommendation: "推荐预览",
  statFavorite: "我的收藏",
  statHistory: "听歌历史",
  statFriends: "好友关注",
  previewRecommendations: "推荐预览",
  previewFavorites: "最近收藏",
  previewHistory: "最近听歌历史",
  previewFriends: "好友关注预览",
  viewAll: "查看全部",
  viewFavorites: "查看收藏",
  viewHistory: "查看历史",
  viewFriends: "查看好友",
  emptyRecommendations: "暂时还没有推荐结果，稍后刷新再试。",
  emptyFavorites: "你还没有收藏歌曲，可以先去搜索页或推荐页看看。",
  emptyHistory: "你还没有听歌历史，点开歌曲试听后这里会自动出现记录。",
  emptyFriends: "你还没有关注好友，关注后推荐会融合好友收藏和听歌历史。",
  scorePrefix: "推荐分数 ",
  recentPlayPrefix: "最近播放：",
  friendIdPrefix: "好友 ID：",
  followTimePrefix: "关注时间：",
  unknownAlbum: "未知专辑",
  userCount: "用户总数",
  itemCount: "歌曲总数",
  interactionCount: "交互总数",
  trainingCount: "训练样本",
  recommendationCount: "推荐结果",
  needUserKeyRecommendations: "请输入用户 ID 或用户名后再查看推荐。",
  needUserKeyHistory: "请输入用户 ID 或用户名后再查看听歌历史。",
  needUserKeyFriends: "请输入用户 ID 或用户名后再查看好友关系。"
};

const router = useRouter();
const username = resolveCurrentUsername();
const userId = resolveCurrentUserId();
const admin = isAdminUser(username);

const busy = ref(false);
const error = ref("");
const stats = ref(null);
const quickUserId = ref("");
const recommendationPreview = ref([]);
const favoritePreview = ref([]);
const historyPreview = ref([]);
const historyTotal = ref(0);
const followingPreview = ref([]);
const followingTotal = ref(0);

const favoriteCount = computed(() => favoritePreview.value.length);

const formatDateTime = (value) => {
  if (!value) {
    return "-";
  }
  return String(value).replace("T", " ").replace(/\.\d+$/, "");
};

const loadAdminData = async () => {
  stats.value = await fetchStats();
};

const loadUserData = async () => {
  if (!userId) {
    throw new Error(text.missingUser);
  }

  const [recommendations, favorites, historyPage, following] = await Promise.all([
    fetchRecommendations(userId, 10),
    fetchFavoriteItems(userId),
    fetchListeningHistory(userId, 1, 3),
    fetchFollowingUsers(userId)
  ]);

  recommendationPreview.value = recommendations.slice(0, 3);
  favoritePreview.value = favorites.slice(0, 3);
  historyPreview.value = Array.isArray(historyPage?.list) ? historyPage.list.slice(0, 3) : [];
  historyTotal.value = Number(historyPage?.total || 0);
  followingPreview.value = Array.isArray(following) ? following.slice(0, 3) : [];
  followingTotal.value = Array.isArray(following) ? following.length : 0;
};

const loadPage = async () => {
  busy.value = true;
  error.value = "";
  try {
    if (admin) {
      await loadAdminData();
    } else {
      await loadUserData();
    }
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};

const openRecommendations = () => {
  router.push({
    name: "home-recommendations",
    params: { userId: userId || quickUserId.value || "" }
  });
};

const openFavorites = () => {
  router.push({
    name: "home-favorites",
    params: { userId }
  });
};

const openSearch = () => {
  router.push({
    name: "home-search",
    params: { userId }
  });
};

const openHistory = () => {
  router.push({
    name: "home-history",
    params: { userId: userId || quickUserId.value || "" }
  });
};

const openFriends = () => {
  router.push({
    name: "home-friends",
    params: { userId: userId || quickUserId.value || "" }
  });
};

const goAdminRecommendations = () => {
  if (!quickUserId.value.trim()) {
    error.value = text.needUserKeyRecommendations;
    return;
  }
  router.push({
    name: "home-recommendations",
    params: { userId: quickUserId.value.trim() }
  });
};

const goAdminHistory = () => {
  if (!quickUserId.value.trim()) {
    error.value = text.needUserKeyHistory;
    return;
  }
  router.push({
    name: "home-history",
    params: { userId: quickUserId.value.trim() }
  });
};

const goAdminFriends = () => {
  if (!quickUserId.value.trim()) {
    error.value = text.needUserKeyFriends;
    return;
  }
  router.push({
    name: "home-friends",
    params: { userId: quickUserId.value.trim() }
  });
};

const goLibrary = () => {
  router.push({ name: "home-library" });
};

onMounted(loadPage);
</script>

<template>
  <div class="page-grid">
    <div class="section-head">
      <h2>{{ admin ? text.adminTitle : text.userTitle }}</h2>
      <button class="btn-secondary" :disabled="busy" type="button" @click="loadPage">
        {{ text.refresh }}
      </button>
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>

    <template v-if="admin">
      <div v-if="stats" class="stats-grid">
        <article class="stat-card">
          <span class="stat-k">{{ text.userCount }}</span>
          <span class="stat-v">{{ stats.userCount }}</span>
        </article>
        <article class="stat-card">
          <span class="stat-k">{{ text.itemCount }}</span>
          <span class="stat-v">{{ stats.itemCount }}</span>
        </article>
        <article class="stat-card">
          <span class="stat-k">{{ text.interactionCount }}</span>
          <span class="stat-v">{{ stats.interactionCount }}</span>
        </article>
        <article class="stat-card">
          <span class="stat-k">{{ text.trainingCount }}</span>
          <span class="stat-v">{{ stats.trainingSampleCount }}</span>
        </article>
        <article class="stat-card">
          <span class="stat-k">{{ text.recommendationCount }}</span>
          <span class="stat-v">{{ stats.recommendationCount }}</span>
        </article>
      </div>

      <section class="panel">
        <h3>{{ text.adminQuick }}</h3>
        <div class="inline-form">
          <input v-model.trim="quickUserId" :placeholder="text.inputUserKey" />
          <button class="btn-primary" type="button" @click="goAdminRecommendations">{{ text.checkRecommendations }}</button>
          <button class="btn-secondary" type="button" @click="goAdminHistory">{{ text.checkHistory }}</button>
          <button class="btn-secondary" type="button" @click="goAdminFriends">{{ text.checkFriends }}</button>
          <button class="btn-secondary" type="button" @click="goLibrary">{{ text.goLibrary }}</button>
        </div>
      </section>
    </template>

    <template v-else>
      <section class="hero-panel">
        <div>
          <p class="helper-text">{{ text.welcome }}</p>
          <h3 class="hero-title">{{ username || text.explorer }}</h3>
          <p class="hero-copy">{{ text.heroCopy }}</p>
        </div>
        <div class="hero-actions">
          <button class="btn-primary" type="button" @click="openRecommendations">{{ text.openRecommendations }}</button>
          <button class="btn-secondary" type="button" @click="openFriends">{{ text.openFriends }}</button>
          <button class="btn-secondary" type="button" @click="openSearch">{{ text.openSearch }}</button>
        </div>
      </section>

      <div class="stats-grid">
        <article class="stat-card">
          <span class="stat-k">{{ text.statRecommendation }}</span>
          <span class="stat-v">{{ recommendationPreview.length }}</span>
        </article>
        <article class="stat-card">
          <span class="stat-k">{{ text.statFavorite }}</span>
          <span class="stat-v">{{ favoriteCount }}</span>
        </article>
        <article class="stat-card">
          <span class="stat-k">{{ text.statHistory }}</span>
          <span class="stat-v">{{ historyTotal }}</span>
        </article>
        <article class="stat-card">
          <span class="stat-k">{{ text.statFriends }}</span>
          <span class="stat-v">{{ followingTotal }}</span>
        </article>
      </div>

      <div class="preview-grid">
        <section class="panel">
          <div class="section-head">
            <h3>{{ text.previewRecommendations }}</h3>
            <button class="btn-secondary mini" type="button" @click="openRecommendations">{{ text.viewAll }}</button>
          </div>
          <div v-if="recommendationPreview.length" class="preview-list">
            <article v-for="item in recommendationPreview" :key="item.itemId" class="preview-item">
              <strong>{{ item.title }}</strong>
              <span class="helper-text">{{ item.artistName }}</span>
              <span class="helper-text">{{ text.scorePrefix }}{{ item.score }}</span>
            </article>
          </div>
          <p v-else class="helper-text">{{ text.emptyRecommendations }}</p>
        </section>

        <section class="panel">
          <div class="section-head">
            <h3>{{ text.previewFavorites }}</h3>
            <button class="btn-secondary mini" type="button" @click="openFavorites">{{ text.viewFavorites }}</button>
          </div>
          <div v-if="favoritePreview.length" class="preview-list">
            <article v-for="item in favoritePreview" :key="item.itemId" class="preview-item">
              <strong>{{ item.title }}</strong>
              <span class="helper-text">{{ item.artistName }}</span>
              <span class="helper-text">{{ item.albumName || text.unknownAlbum }}</span>
            </article>
          </div>
          <p v-else class="helper-text">{{ text.emptyFavorites }}</p>
        </section>

        <section class="panel">
          <div class="section-head">
            <h3>{{ text.previewHistory }}</h3>
            <button class="btn-secondary mini" type="button" @click="openHistory">{{ text.viewHistory }}</button>
          </div>
          <div v-if="historyPreview.length" class="preview-list">
            <article v-for="item in historyPreview" :key="`${item.userId}-${item.itemId}`" class="preview-item">
              <strong>{{ item.title }}</strong>
              <span class="helper-text">{{ item.artistName }}</span>
              <span class="helper-text">{{ text.recentPlayPrefix }}{{ formatDateTime(item.lastListenTime) }}</span>
            </article>
          </div>
          <p v-else class="helper-text">{{ text.emptyHistory }}</p>
        </section>

        <section class="panel">
          <div class="section-head">
            <h3>{{ text.previewFriends }}</h3>
            <button class="btn-secondary mini" type="button" @click="openFriends">{{ text.viewFriends }}</button>
          </div>
          <div v-if="followingPreview.length" class="preview-list">
            <article v-for="item in followingPreview" :key="`${item.followerUserId}-${item.followeeUserId}`" class="preview-item">
              <strong>{{ item.followeeUsername || `ID ${item.followeeUserId}` }}</strong>
              <span class="helper-text">{{ text.friendIdPrefix }}{{ item.followeeUserId }}</span>
              <span class="helper-text">{{ text.followTimePrefix }}{{ formatDateTime(item.followTime) }}</span>
            </article>
          </div>
          <p v-else class="helper-text">{{ text.emptyFriends }}</p>
        </section>
      </div>
    </template>
  </div>
</template>
