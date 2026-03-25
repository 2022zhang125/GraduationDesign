<script setup>
import { onMounted, ref, watch } from "vue";
import { fetchFavoriteItems, removeFavoriteItem } from "../api";
import { prefetchTrackAssets, scheduleTrackAssetPrefetch } from "../composables/useTrackAssetCache";
import { useGlobalAudioPlayer } from "../composables/useGlobalAudioPlayer";
import { isAdminUser, resolveCurrentUserId, resolveCurrentUsername } from "../utils/session";

const props = defineProps({
  userId: {
    type: [String, Number],
    default: ""
  }
});

const { playItem, setTrackAssets } = useGlobalAudioPlayer();

const admin = isAdminUser(resolveCurrentUsername());
const selfUserId = resolveCurrentUserId();
const selectedUserId = ref(props.userId ? String(props.userId) : selfUserId);
const favorites = ref([]);
const busy = ref(false);
const error = ref("");

const syncSelectedUserId = (value) => {
  if (admin) {
    selectedUserId.value = value ? String(value) : "";
    return;
  }
  selectedUserId.value = selfUserId;
};

const loadFavorites = async () => {
  if (!selectedUserId.value) {
    error.value = admin ? "请输入要查看的用户 ID。" : "未获取到当前用户信息，请重新登录。";
    favorites.value = [];
    return;
  }

  busy.value = true;
  error.value = "";

  try {
    favorites.value = await fetchFavoriteItems(selectedUserId.value);
    scheduleTrackAssetPrefetch(favorites.value);
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};

const removeFavorite = async (itemId) => {
  busy.value = true;
  error.value = "";

  try {
    await removeFavoriteItem(selectedUserId.value, itemId);
    favorites.value = favorites.value.filter((item) => item.itemId !== itemId);
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};

const playTrack = async (row) => {
  busy.value = true;
  error.value = "";

  try {
    await playItem(row.itemId, row);
    void prefetchTrackAssets(row)
      .then((assets) => {
        if (assets) {
          setTrackAssets(assets);
        }
      })
      .catch(() => {});
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};

watch(
  () => props.userId,
  async (value) => {
    syncSelectedUserId(value);
    if (selectedUserId.value) {
      await loadFavorites();
    }
  }
);

onMounted(() => {
  syncSelectedUserId(props.userId);
  if (selectedUserId.value) {
    loadFavorites();
  }
});
</script>

<template>
  <div class="page-grid">
    <div class="section-head">
      <h2>{{ admin ? "收藏查看" : "我的收藏" }}</h2>
      <div class="inline-form">
        <input
          v-if="admin"
          v-model.trim="selectedUserId"
          placeholder="输入用户 ID 查看收藏"
        />
        <button class="btn-primary" :disabled="busy" type="button" @click="loadFavorites">
          刷新收藏
        </button>
      </div>
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>

    <table v-if="favorites.length" class="ncf-table">
      <thead>
        <tr>
          <th>歌曲 ID</th>
          <th>歌曲名</th>
          <th>歌手</th>
          <th>专辑</th>
          <th>收藏时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in favorites" :key="`${row.userId}-${row.itemId}`">
          <td>{{ row.itemId }}</td>
          <td>{{ row.title }}</td>
          <td>{{ row.artistName }}</td>
          <td>{{ row.albumName || "-" }}</td>
          <td>{{ row.favoriteTime }}</td>
          <td class="inline-form">
            <button class="btn-secondary mini" type="button" @click="playTrack(row)">试听</button>
            <button class="btn-danger mini" type="button" @click="removeFavorite(row.itemId)">
              取消收藏
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-else class="helper-text">
      {{ admin ? "输入用户 ID 后即可查看该用户收藏。" : "你还没有收藏歌曲，可以先去搜索或推荐页看看。" }}
    </div>
  </div>
</template>
