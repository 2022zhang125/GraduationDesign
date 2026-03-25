<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { fetchHistoryFeed, fetchListeningHistory } from "../api";
import { prefetchTrackAssets, scheduleTrackAssetPrefetch } from "../composables/useTrackAssetCache";
import { useGlobalAudioPlayer } from "../composables/useGlobalAudioPlayer";
import { isAdminUser, resolveCurrentUserId, resolveCurrentUsername } from "../utils/session";

const props = defineProps({
  userId: {
    type: [String, Number],
    default: ""
  }
});

const router = useRouter();
const { playItem, setTrackAssets } = useGlobalAudioPlayer();
const currentUsername = resolveCurrentUsername();
const admin = isAdminUser(currentUsername);
const selfUserId = resolveCurrentUserId();

const selectedUserId = ref(props.userId ? String(props.userId) : selfUserId);
const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const loading = ref(false);
const error = ref("");

const pageTitle = computed(() => (admin ? "听歌历史查看" : "我的听歌历史"));
const emptyText = computed(() =>
  admin
    ? "输入用户 ID 后可查看对应用户的听歌历史，也可以直接查看全站最近活跃歌曲。"
    : "你还没有听歌历史，先去推荐、收藏或搜索页点几首歌试听吧。"
);

const formatDateTime = (value) => {
  if (!value) {
    return "-";
  }

  const normalized = String(value).replace("T", " ").replace(/\.\d+$/, "");
  return normalized;
};

const formatDuration = (value) => {
  const seconds = Number(value || 0);
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return "0 秒";
  }

  if (seconds < 60) {
    return `${seconds} 秒`;
  }

  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainSeconds = seconds % 60;

  if (hours > 0) {
    return `${hours} 小时 ${minutes} 分 ${remainSeconds} 秒`;
  }
  return `${minutes} 分 ${remainSeconds} 秒`;
};

const syncSelectedUserId = (value) => {
  if (admin) {
    selectedUserId.value = value ? String(value) : "";
    return;
  }
  selectedUserId.value = selfUserId;
};

const loadHistory = async () => {
  loading.value = true;
  error.value = "";
  try {
    const normalizedUserId = String(selectedUserId.value || "").trim();
    const result = admin
      ? await fetchHistoryFeed({
          userId: normalizedUserId || undefined,
          page: page.value,
          size: size.value
        })
      : await fetchListeningHistory(normalizedUserId || selfUserId, page.value, size.value);

    rows.value = Array.isArray(result?.list) ? result.list : [];
    total.value = Number(result?.total || 0);
    scheduleTrackAssetPrefetch(rows.value);

    if (admin) {
      router.replace({
        name: "home-history",
        params: normalizedUserId ? { userId: normalizedUserId } : {},
        query: {
          page: String(page.value),
          size: String(size.value)
        }
      });
    }
  } catch (e) {
    rows.value = [];
    total.value = 0;
    error.value = e.message;
  } finally {
    loading.value = false;
  }
};

const playTrack = async (row) => {
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
  }
};

watch(
  () => props.userId,
  (value) => {
    syncSelectedUserId(value);
    page.value = 1;
    loadHistory();
  }
);

onMounted(() => {
  syncSelectedUserId(props.userId);
  loadHistory();
});
</script>

<template>
  <div class="page-grid">
    <div class="section-head">
      <h2>{{ pageTitle }}</h2>
      <div class="inline-form">
        <input v-if="admin" v-model.trim="selectedUserId" placeholder="用户 ID 或用户名，可留空查看全站" />
        <input v-model.number="page" min="1" placeholder="页码" type="number" />
        <input v-model.number="size" min="1" placeholder="每页条数" type="number" />
        <button class="btn-secondary" :disabled="loading" type="button" @click="loadHistory">
          刷新历史
        </button>
      </div>
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>
    <p class="helper-text">共 {{ total }} 条最近听歌记录</p>

    <table v-if="rows.length" class="ncf-table">
      <thead>
        <tr>
          <th v-if="admin">用户</th>
          <th>歌曲 ID</th>
          <th>歌曲</th>
          <th>歌手</th>
          <th>专辑</th>
          <th>最近播放</th>
          <th>播放次数</th>
          <th>完整播放</th>
          <th>累计时长</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="`${row.userId}-${row.itemId}`">
          <td v-if="admin">
            <div class="history-user-cell">
              <strong>{{ row.username || `用户${row.userId}` }}</strong>
              <span class="helper-text">ID {{ row.userId }}</span>
            </div>
          </td>
          <td>{{ row.itemId }}</td>
          <td>{{ row.title }}</td>
          <td>{{ row.artistName }}</td>
          <td>{{ row.albumName || "-" }}</td>
          <td>{{ formatDateTime(row.lastListenTime) }}</td>
          <td>{{ row.playCount || 0 }}</td>
          <td>{{ row.completePlayCount || 0 }}</td>
          <td>{{ formatDuration(row.playedDurationSeconds) }}</td>
          <td>
            <button class="btn-secondary mini" type="button" @click="playTrack(row)">试听</button>
          </td>
        </tr>
      </tbody>
    </table>

    <section v-else class="panel panel-empty">
      <p class="helper-text">{{ emptyText }}</p>
    </section>
  </div>
</template>

