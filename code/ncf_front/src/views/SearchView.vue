<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  addFavoriteItem,
  fetchFavoriteItems,
  fetchMusicPlaybackDetail,
  removeFavoriteItem,
  searchMusic
} from "../api";
import { useGlobalAudioPlayer } from "../composables/useGlobalAudioPlayer";
import { prefetchTrackAssets } from "../composables/useTrackAssetCache";
import { isAdminUser, resolveCurrentUserId, resolveCurrentUsername } from "../utils/session";

const props = defineProps({
  userId: {
    type: [String, Number],
    default: ""
  }
});

const route = useRoute();
const router = useRouter();
const { playTrack, setTrackAssets, isSameAsCurrentTrack } = useGlobalAudioPlayer();

const text = {
  title: "歌曲搜索",
  intro: "支持网易云 `wyvip` 与 QQ 音乐 `qq_plus`，可按音源和音质筛选搜索结果。",
  adminUserPlaceholder: "管理员可指定用户 ID 或用户名后再进行收藏操作",
  keywordPlaceholder: "输入歌曲名、歌手或专辑",
  search: "搜索",
  clear: "清空",
  results: "搜索结果",
  resultSummary: "关键词：",
  sourceLabel: "音源：",
  qualityLabel: "音质：",
  loading: "正在聚合网易云与 QQ 音乐搜索结果...",
  inCatalog: "已入库",
  missingAlbum: "未提供专辑信息",
  itemId: "曲库 ID",
  source: "来源",
  localCatalog: "本地曲库",
  noSnippet: "暂无歌词摘要",
  preview: "试听",
  addFavorite: "加入收藏",
  removeFavorite: "取消收藏",
  noResult: "没有搜到匹配歌曲，换个关键词再试试。",
  empty: "输入关键词后即可开始搜索，列表会同时展示所选音源的多条结果。",
  needKeyword: "请输入歌曲名、歌手或专辑关键词。",
  needFavoriteAdmin: "请输入用户 ID 或用户名后再操作收藏。",
  needFavoriteUser: "未获取到当前用户信息，请重新登录。",
  missingCatalogItem: "该搜索结果尚未映射到本地曲库，暂时不能加入收藏。",
  standard: "标准",
  exhigh: "极高",
  lossless: "无损",
  allSources: "全部音源",
  wyvip: "网易云",
  qqPlus: "QQ 音乐"
};

const admin = isAdminUser(resolveCurrentUsername());
const selfUserId = resolveCurrentUserId();
const qualityOptions = [
  { label: text.standard, value: "standard" },
  { label: text.exhigh, value: "exhigh" },
  { label: text.lossless, value: "lossless" }
];
const sourceOptions = [
  { label: text.allSources, value: "all" },
  { label: text.wyvip, value: "wyvip" },
  { label: text.qqPlus, value: "qq_plus" }
];

const toNumericItemId = (value) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
};

const selectedUserId = ref(props.userId ? String(props.userId) : selfUserId);
const keyword = ref(String(route.query.q || ""));
const level = ref(String(route.query.level || "standard"));
const source = ref(String(route.query.source || "all"));
const results = ref([]);
const favoriteItemIds = ref(new Set());
const loading = ref(false);
const hasSearched = ref(false);
const error = ref("");
const searchDebounceTimer = ref(null);
const searchRequestId = ref(0);
const activeSearchSignature = ref("");

const buildSearchSignature = () =>
  [keyword.value.trim().toLowerCase(), source.value, level.value].join("::");

const clearPendingSearch = () => {
  if (searchDebounceTimer.value) {
    clearTimeout(searchDebounceTimer.value);
    searchDebounceTimer.value = null;
  }
};

const syncSelectedUserId = (value) => {
  if (admin) {
    selectedUserId.value = value ? String(value) : "";
    return;
  }
  selectedUserId.value = selfUserId;
};

const syncRoute = () => {
  const query = {};
  if (keyword.value) {
    query.q = keyword.value;
  }
  if (level.value) {
    query.level = level.value;
  }
  if (source.value) {
    query.source = source.value;
  }

  router.replace({
    name: "home-search",
    params: selectedUserId.value ? { userId: selectedUserId.value } : {},
    query
  });
};

const loadFavorites = async () => {
  if (!selectedUserId.value) {
    favoriteItemIds.value = new Set();
    return;
  }

  const list = await fetchFavoriteItems(selectedUserId.value);
  favoriteItemIds.value = new Set(list.map((item) => Number(item.itemId)));
};

const executeSearch = async () => {
  const trimmedKeyword = keyword.value.trim();
  if (!trimmedKeyword) {
    results.value = [];
    hasSearched.value = false;
    error.value = text.needKeyword;
    activeSearchSignature.value = "";
    syncRoute();
    return;
  }

  const signature = buildSearchSignature();
  if (loading.value && activeSearchSignature.value === signature) {
    return;
  }

  const requestId = searchRequestId.value + 1;
  searchRequestId.value = requestId;
  activeSearchSignature.value = signature;
  loading.value = true;
  error.value = "";
  hasSearched.value = true;
  syncRoute();

  try {
    const response = await searchMusic(trimmedKeyword, {
      limit: 15,
      level: level.value,
      source: source.value
    });

    if (requestId !== searchRequestId.value) {
      return;
    }

    results.value = response;
    await loadFavorites();
  } catch (e) {
    if (requestId !== searchRequestId.value) {
      return;
    }
    results.value = [];
    error.value = e.message;
  } finally {
    if (requestId === searchRequestId.value) {
      loading.value = false;
    }
  }
};

const scheduleSearch = () => {
  clearPendingSearch();
  searchDebounceTimer.value = setTimeout(() => {
    searchDebounceTimer.value = null;
    void executeSearch();
  }, 400);
};

const isFavorite = (itemId) => itemId && favoriteItemIds.value.has(Number(itemId));

const toggleFavorite = async (row) => {
  if (!row.itemId) {
    error.value = text.missingCatalogItem;
    return;
  }

  if (!selectedUserId.value) {
    error.value = admin ? text.needFavoriteAdmin : text.needFavoriteUser;
    return;
  }

  loading.value = true;
  error.value = "";

  try {
    const nextSet = new Set(favoriteItemIds.value);
    if (isFavorite(row.itemId)) {
      await removeFavoriteItem(selectedUserId.value, row.itemId);
      nextSet.delete(Number(row.itemId));
    } else {
      await addFavoriteItem(selectedUserId.value, row.itemId);
      nextSet.add(Number(row.itemId));
    }
    favoriteItemIds.value = nextSet;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
};

const playResult = async (row) => {
  error.value = "";

  if (
    isSameAsCurrentTrack(
      {
        itemId: toNumericItemId(row.itemId),
        title: row.title,
        artistName: row.artistName,
        albumName: row.albumName,
        previewUrl: row.playUrl || row.musicUrl || "",
        playUrl: row.playUrl || row.musicUrl || "",
        musicUrl: row.musicUrl || row.playUrl || ""
      },
      {},
      { requirePlaying: true }
    )
  ) {
    return;
  }

  try {
    const detail = await fetchMusicPlaybackDetail({
      query: row.query,
      n: row.trackIndex,
      source: row.source,
      level: row.qualityLevel || level.value,
      itemId: toNumericItemId(row.itemId),
      title: row.title,
      album: row.albumName,
      artist: row.artistName
    });
    const localPlayUrl = detail.playUrl || row.playUrl || detail.musicUrl || row.musicUrl || "";
    playTrack({
      itemId: toNumericItemId(row.itemId),
      title: detail.title || row.title,
      artistName: detail.artistName || row.artistName,
      albumName: detail.albumName || row.albumName,
      previewUrl: localPlayUrl,
      playUrl: localPlayUrl,
      musicUrl: localPlayUrl,
      lyricSnippet: detail.lyricsText || row.lyricSnippet || "",
      sourcePlatform: detail.sourcePlatform || row.sourcePlatform,
      coverUrl: detail.coverUrl || row.coverUrl
    });
    void prefetchTrackAssets({
      ...row,
      title: detail.title || row.title,
      artistName: detail.artistName || row.artistName,
      albumName: detail.albumName || row.albumName
    })
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

const clearSearch = () => {
  clearPendingSearch();
  searchRequestId.value += 1;
  activeSearchSignature.value = "";
  keyword.value = "";
  results.value = [];
  hasSearched.value = false;
  error.value = "";
  loading.value = false;
  syncRoute();
};

watch(
  () => props.userId,
  async (value) => {
    syncSelectedUserId(value);
    try {
      await loadFavorites();
    } catch (e) {
      error.value = e.message;
    }
  }
);

watch(
  () => route.query.q,
  (value) => {
    keyword.value = String(value || "");
  }
);

watch(
  () => route.query.level,
  (value) => {
    level.value = String(value || "standard");
  }
);

watch(
  () => route.query.source,
  (value) => {
    source.value = String(value || "all");
  }
);

onBeforeUnmount(() => {
  clearPendingSearch();
});

onMounted(async () => {
  syncSelectedUserId(props.userId);
  if (keyword.value.trim()) {
    await executeSearch();
  } else {
    await loadFavorites();
  }
});
</script>

<template>
  <div class="page-grid">
    <div class="section-head">
      <h2>{{ text.title }}</h2>
      <span class="helper-text">{{ text.intro }}</span>
    </div>

    <section class="panel">
      <div class="inline-form search-toolbar">
        <input
          v-if="admin"
          v-model.trim="selectedUserId"
          :placeholder="text.adminUserPlaceholder"
        />
        <input
          v-model.trim="keyword"
          class="search-input"
          :placeholder="text.keywordPlaceholder"
          @keyup.enter="scheduleSearch"
        />
        <select v-model="source" class="source-select">
          <option v-for="option in sourceOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
        <select v-model="level" class="quality-select" :disabled="source === 'qq_plus'">
          <option v-for="option in qualityOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
        <button class="btn-primary" :disabled="loading" type="button" @click="scheduleSearch">
          {{ text.search }}
        </button>
        <button class="btn-secondary" :disabled="loading" type="button" @click="clearSearch">
          {{ text.clear }}
        </button>
      </div>
    </section>

    <p v-if="error" class="error-text">{{ error }}</p>

    <section class="panel">
      <div class="section-head">
        <h3>{{ text.results }}</h3>
        <span v-if="keyword" class="helper-text">
          {{ text.resultSummary }}{{ keyword }} | {{ text.sourceLabel }}{{ source }} | {{ text.qualityLabel }}{{ level }}
        </span>
      </div>

      <div v-if="loading" class="loading-panel">
        <span class="loading-spinner" />
        <p class="helper-text">{{ text.loading }}</p>
      </div>

      <div v-else-if="results.length" class="search-results-grid">
        <article
          v-for="row in results"
          :key="`${row.source}-${row.query}-${row.trackIndex}-${row.title}-${row.artistName}`"
          class="search-result-card"
        >
          <div class="search-result-card__head">
            <div class="search-result-card__badges">
              <span
                class="search-result-card__source"
                :class="{
                  'search-result-card__source--wyvip': row.source === 'wyvip',
                  'search-result-card__source--qq': row.source === 'qq_plus'
                }"
              >
                {{ row.sourceLabel }}
              </span>
              <span v-if="row.payTag" class="search-result-card__tag">
                {{ row.payTag }}
              </span>
              <span v-if="row.inCatalog" class="search-result-card__tag">
                {{ text.inCatalog }}
              </span>
            </div>
            <span class="search-result-card__index">#{{ row.trackIndex || "-" }}</span>
          </div>

          <div class="search-result-card__body">
            <h4 class="search-result-card__title">{{ row.title }}</h4>
            <p class="search-result-card__artist">{{ row.artistName || "-" }}</p>
            <p class="search-result-card__album">{{ row.albumName || text.missingAlbum }}</p>
          </div>

          <dl class="search-result-card__meta">
            <div>
              <dt>{{ text.itemId }}</dt>
              <dd>{{ row.itemId || "-" }}</dd>
            </div>
            <div>
              <dt>{{ text.source }}</dt>
              <dd>{{ row.inCatalog ? `${text.localCatalog} + ${row.sourceLabel}` : row.sourceLabel }}</dd>
            </div>
          </dl>

          <div class="search-result-card__lyrics">
            {{ row.lyricSnippet || text.noSnippet }}
          </div>

          <div class="search-result-card__actions">
            <button
              class="btn-secondary"
              :disabled="!row.playUrl"
              type="button"
              @click="playResult(row)"
            >
              {{ text.preview }}
            </button>
            <button
              :class="isFavorite(row.itemId) ? 'btn-danger' : 'btn-primary'"
              :disabled="!row.itemId || loading"
              type="button"
              @click="toggleFavorite(row)"
            >
              {{ isFavorite(row.itemId) ? text.removeFavorite : text.addFavorite }}
            </button>
          </div>
        </article>
      </div>

      <div v-else-if="hasSearched" class="helper-text">{{ text.noResult }}</div>
      <div v-else class="helper-text">{{ text.empty }}</div>
    </section>
  </div>
</template>

<style scoped>
.search-toolbar {
  align-items: center;
  flex-wrap: wrap;
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) repeat(2, minmax(140px, 0.32fr)) auto auto;
  gap: 10px;
}

.quality-select,
.source-select {
  min-width: 120px;
}

.search-results-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 14px;
}

.search-result-card {
  display: grid;
  gap: 14px;
  min-width: 0;
  padding: 16px;
  border: 1px solid rgba(54, 6, 77, 0.12);
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(118, 210, 219, 0.08)),
    #fff;
  box-shadow: 0 12px 24px rgba(54, 6, 77, 0.06);
}

.search-result-card__head,
.search-result-card__meta,
.search-result-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.search-result-card__badges {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.search-result-card__source,
.search-result-card__tag,
.search-result-card__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.search-result-card__source {
  background: rgba(54, 6, 77, 0.08);
  color: var(--ink);
}

.search-result-card__source--wyvip {
  background: rgba(17, 110, 88, 0.12);
  color: #116e58;
}

.search-result-card__source--qq {
  background: rgba(34, 110, 186, 0.12);
  color: #226eba;
}

.search-result-card__tag {
  background: rgba(218, 72, 72, 0.08);
  color: var(--danger);
}

.search-result-card__index {
  background: rgba(54, 6, 77, 0.06);
}

.search-result-card__body {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.search-result-card__title,
.search-result-card__artist,
.search-result-card__album {
  margin: 0;
}

.search-result-card__title {
  font-size: 18px;
  line-height: 1.35;
  word-break: break-word;
}

.search-result-card__artist {
  font-size: 14px;
  font-weight: 700;
}

.search-result-card__album {
  font-size: 13px;
  opacity: 0.72;
}

.search-result-card__meta {
  align-items: stretch;
}

.search-result-card__meta > div {
  flex: 1 1 0;
  min-width: 0;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(54, 6, 77, 0.04);
}

.search-result-card__meta dt,
.search-result-card__meta dd {
  margin: 0;
}

.search-result-card__meta dt {
  margin-bottom: 4px;
  font-size: 12px;
  opacity: 0.72;
}

.search-result-card__meta dd {
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.search-result-card__lyrics {
  min-height: 92px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 253, 245, 0.9);
  border: 1px solid rgba(54, 6, 77, 0.08);
  font-size: 13px;
  line-height: 1.7;
  color: rgba(54, 6, 77, 0.82);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  overflow: hidden;
}

.search-result-card__actions {
  justify-content: stretch;
}

.search-result-card__actions > button {
  flex: 1 1 0;
  min-height: 42px;
}

@media (max-width: 768px) {
  .search-toolbar {
    grid-template-columns: 1fr;
  }

  .search-toolbar > * {
    width: 100%;
  }

  .search-results-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .search-result-card {
    gap: 12px;
    padding: 14px;
    border-radius: 16px;
  }

  .search-result-card__head,
  .search-result-card__meta,
  .search-result-card__actions {
    display: grid;
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .search-result-card__index {
    justify-self: start;
  }

  .search-result-card__meta > div {
    padding: 10px;
  }

  .search-result-card__lyrics {
    min-height: 0;
    -webkit-line-clamp: 5;
  }
}

@media (max-width: 480px) {
  .search-result-card {
    padding: 12px;
  }

  .search-result-card__title {
    font-size: 16px;
  }

  .search-result-card__lyrics {
    padding: 10px 12px;
    font-size: 12px;
  }

  .search-result-card__actions > button {
    min-height: 40px;
  }
}
</style>


