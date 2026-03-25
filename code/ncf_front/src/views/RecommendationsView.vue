<script setup>
import { onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  addFavoriteItem,
  fetchExplanation,
  fetchFavoriteItems,
  fetchRecommendations,
  removeFavoriteItem
} from "../api";
import { prefetchTrackAssets, scheduleTrackAssetPrefetch } from "../composables/useTrackAssetCache";
import { useGlobalAudioPlayer } from "../composables/useGlobalAudioPlayer";
import { isAdminUser, resolveCurrentUserId, resolveCurrentUsername } from "../utils/session";

const props = defineProps({
  userId: {
    type: [String, Number],
    default: ""
  }
});

const route = useRoute();
const router = useRouter();
const { playItem, setTrackAssets } = useGlobalAudioPlayer();

const text = {
  adminTitle: "推荐查看",
  userTitle: "我的每日推荐",
  sourcePrefix: "来源：",
  inputUserKey: "输入用户 ID 或用户名查看推荐",
  refresh: "刷新推荐",
  openFavorites: "查看收藏",
  needUserAdmin: "请输入用户 ID 或用户名后再查看推荐。",
  needUserSelf: "未获取到当前用户信息，请重新登录。",
  rank: "排名",
  itemId: "歌曲 ID",
  title: "歌曲名",
  artist: "歌手",
  score: "分数",
  action: "操作",
  viewExplanation: "查看解释",
  playPreview: "试听",
  addFavorite: "加入收藏",
  removeFavorite: "取消收藏",
  emptyAdmin: "输入用户 ID 或用户名后即可查看对应的推荐列表。",
  emptyUser: "暂时还没有推荐结果，稍后刷新再试。",
  explanationTitle: "推荐解释",
  sourceOverview: "总览页",
  sourceLibrary: "曲库管理",
  sourceRecommendations: "推荐列表",
  sourceSearch: "搜索页",
  usernameFallback: "当前用户",
  needFavoritesAdmin: "请输入用户 ID 或用户名后再查看收藏。"
};

const sourceLabelMap = {
  overview: text.sourceOverview,
  library: text.sourceLibrary,
  recommendations: text.sourceRecommendations,
  search: text.sourceSearch
};

const currentUsername = resolveCurrentUsername();
const admin = isAdminUser(currentUsername);
const selfUserId = resolveCurrentUserId();
const selectedUserId = ref(props.userId ? String(props.userId) : selfUserId);
const recommendations = ref([]);
const favoriteItemIds = ref(new Set());
const explanation = ref("");
const busy = ref(false);
const error = ref("");

const syncSelectedUserId = (value) => {
  if (admin) {
    selectedUserId.value = value ? String(value) : "";
    return;
  }
  selectedUserId.value = selfUserId;
};

const escapeRegExp = (value) => String(value || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const normalizeExplanationText = (rawText) => {
  const raw = String(rawText || "");
  const userId = String(selectedUserId.value || "").trim();
  const username = currentUsername || text.usernameFallback;

  if (!raw) {
    return "";
  }

  let normalized = raw;
  if (userId) {
    const safeUserId = escapeRegExp(userId);
    normalized = normalized
      .replace(new RegExp(`\给\用\户\\s*${safeUserId}`, "g"), `给用户${username}`)
      .replace(new RegExp(`\用\户\\s*${safeUserId}`, "g"), `用户${username}`)
      .replace(new RegExp(`for user\\s*${safeUserId}`, "gi"), `给用户${username}`)
      .replace(new RegExp(`user\\s*${safeUserId}`, "gi"), `用户${username}`);
  }
  return normalized;
};

const loadFavorites = async () => {
  if (!selectedUserId.value) {
    favoriteItemIds.value = new Set();
    return;
  }

  const list = await fetchFavoriteItems(selectedUserId.value);
  favoriteItemIds.value = new Set(list.map((item) => Number(item.itemId)));
};

const filterRecommendations = (list) =>
  list
    .filter((row) => !favoriteItemIds.value.has(Number(row.itemId)))
    .slice(0, 10)
    .map((row, index) => ({
      ...row,
      rankNo: index + 1
    }));

const loadRecommendations = async () => {
  if (!selectedUserId.value) {
    error.value = admin ? text.needUserAdmin : text.needUserSelf;
    recommendations.value = [];
    return;
  }

  busy.value = true;
  error.value = "";
  explanation.value = "";

  try {
    await loadFavorites();
    const list = await fetchRecommendations(selectedUserId.value, 30);
    recommendations.value = filterRecommendations(list);
    scheduleTrackAssetPrefetch(recommendations.value);
    router.replace({
      name: "home-recommendations",
      params: { userId: selectedUserId.value },
      query: { ...route.query }
    });
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};

const loadExplanation = async (itemId) => {
  busy.value = true;
  error.value = "";

  try {
    explanation.value = normalizeExplanationText(
      await fetchExplanation(selectedUserId.value, itemId, 10)
    );
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

const isFavorite = (itemId) => favoriteItemIds.value.has(Number(itemId));

const toggleFavorite = async (row) => {
  if (!selectedUserId.value) {
    error.value = admin ? text.needFavoritesAdmin : text.needUserSelf;
    return;
  }

  busy.value = true;
  error.value = "";

  try {
    if (isFavorite(row.itemId)) {
      await removeFavoriteItem(selectedUserId.value, row.itemId);
    } else {
      await addFavoriteItem(selectedUserId.value, row.itemId);
    }
    await loadRecommendations();
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};

const goFavorites = () => {
  if (!selectedUserId.value) {
    error.value = admin ? text.needFavoritesAdmin : text.needUserSelf;
    return;
  }

  router.push({
    name: "home-favorites",
    params: { userId: selectedUserId.value },
    query: { from: "recommendations" }
  });
};

watch(
  () => props.userId,
  async (value) => {
    syncSelectedUserId(value);
    if (selectedUserId.value) {
      await loadRecommendations();
    }
  }
);

onMounted(async () => {
  syncSelectedUserId(props.userId);
  if (selectedUserId.value) {
    await loadRecommendations();
  }
});
</script>

<template>
  <div class="page-grid">
    <div class="section-head">
      <h2>{{ admin ? text.adminTitle : text.userTitle }}</h2>
      <span v-if="route.query.from" class="helper-text">
        {{ text.sourcePrefix }}{{ sourceLabelMap[route.query.from] || route.query.from }}
      </span>
    </div>

    <div class="inline-form">
      <input
        v-if="admin"
        v-model.trim="selectedUserId"
        :placeholder="text.inputUserKey"
      />
      <button class="btn-primary" :disabled="busy" type="button" @click="loadRecommendations">
        {{ text.refresh }}
      </button>
      <button class="btn-secondary" :disabled="busy" type="button" @click="goFavorites">
        {{ text.openFavorites }}
      </button>
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>

    <table v-if="recommendations.length" class="ncf-table recommendation-table">
      <thead>
        <tr>
          <th>{{ text.rank }}</th>
          <th>{{ text.itemId }}</th>
          <th>{{ text.title }}</th>
          <th>{{ text.artist }}</th>
          <th>{{ text.score }}</th>
          <th>{{ text.action }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in recommendations" :key="`${row.itemId}-${row.rankNo}`">
          <td>{{ row.rankNo }}</td>
          <td>{{ row.itemId }}</td>
          <td>{{ row.title }}</td>
          <td>{{ row.artistName }}</td>
          <td>{{ row.score }}</td>
          <td>
            <div class="table-actions-row recommendation-actions">
              <button class="btn-secondary mini" type="button" @click="loadExplanation(row.itemId)">
                {{ text.viewExplanation }}
              </button>
              <button class="btn-secondary mini" type="button" @click="playTrack(row)">
                {{ text.playPreview }}
              </button>
              <button
                class="mini"
                :class="isFavorite(row.itemId) ? 'btn-danger' : 'btn-primary'"
                type="button"
                @click="toggleFavorite(row)"
              >
                {{ isFavorite(row.itemId) ? text.removeFavorite : text.addFavorite }}
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-else class="helper-text">
      {{ admin ? text.emptyAdmin : text.emptyUser }}
    </div>

    <section v-if="explanation" class="explain-box">
      <h3>{{ text.explanationTitle }}</h3>
      <p>{{ explanation }}</p>
    </section>
  </div>
</template>
