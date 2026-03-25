<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { API_BASE_URL, getToken, reportPlayComplete, reportPlayStart } from "../api";
import { getCachedTrackAssets, prefetchTrackAssets } from "../composables/useTrackAssetCache";
import { useGlobalAudioPlayer } from "../composables/useGlobalAudioPlayer";
import { resolveCurrentUserId } from "../utils/session";

const audioRef = ref(null);
const lyricsData = ref(null);
const lyricLoading = ref(false);
const lyricError = ref("");
const activeSessionTrack = ref(null);
const reportedSeconds = ref(0);
const sessionStarted = ref(false);

const mediaSessionSupported = typeof navigator !== "undefined" && "mediaSession" in navigator;
const mediaMetadataSupported = typeof window !== "undefined" && "MediaMetadata" in window;

const {
  currentTrack,
  currentSrc,
  currentTime,
  duration,
  isPlaying,
  error,
  playNonce,
  clearTrack,
  clearError,
  updatePlaybackState
} = useGlobalAudioPlayer();

const parseTimestamp = (token) => {
  const matched = token.match(/(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?/);
  if (!matched) {
    return null;
  }

  const minutes = Number(matched[1] || 0);
  const seconds = Number(matched[2] || 0);
  const fractionRaw = matched[3] || "0";
  const fraction =
    fractionRaw.length === 3
      ? Number(fractionRaw) / 1000
      : Number(fractionRaw) / Math.pow(10, fractionRaw.length);

  return minutes * 60 + seconds + fraction;
};

const normalizeLyricLineText = (value) =>
  String(value || "")
    .replace(/<[^>]+>/g, " ")
    .replace(/\s+/g, " ")
    .trim();

const formatTime = (value) => {
  if (!Number.isFinite(value) || value < 0) {
    return "00:00";
  }

  const totalSeconds = Math.floor(value);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
};

const parsedLyrics = computed(() => {
  const rawLyrics = String(lyricsData.value?.lyricsText || "");
  if (!rawLyrics) {
    return [];
  }

  const durationSeconds = durationValue.value > 0 ? durationValue.value : 0;
  const rawLines = rawLyrics.split(/\r?\n/);
  const timedLines = [];
  const plainLines = [];
  let offsetSeconds = 0;

  rawLines.forEach((rawLine, lineIndex) => {
    const line = String(rawLine || "").trim();
    if (!line) {
      return;
    }

    const offsetMatch = line.match(/^\[offset:([+-]?\d+)\]$/i);
    if (offsetMatch) {
      offsetSeconds = Number(offsetMatch[1] || 0) / 1000;
      return;
    }

    if (/^\[(ti|ar|al|by|re|ve):/i.test(line)) {
      return;
    }

    const timestamps = [...line.matchAll(/\[(\d{1,2}:\d{2}(?:[.:]\d{1,3})?)\]/g)];
    const text = normalizeLyricLineText(line.replace(/\[[^\]]*]/g, " "));
    if (!text) {
      return;
    }

    if (!timestamps.length) {
      plainLines.push({
        id: `plain-${lineIndex}`,
        text
      });
      return;
    }

    timestamps.forEach((timestamp, timestampIndex) => {
      const seconds = parseTimestamp(timestamp[1]);
      if (seconds === null) {
        return;
      }

      timedLines.push({
        id: `${lineIndex}-${timestampIndex}-${seconds}`,
        time: Math.max(0, seconds + offsetSeconds),
        text
      });
    });
  });

  const mergeTimedLines = (lines) => {
    const merged = [];
    [...lines]
      .sort((left, right) => left.time - right.time)
      .forEach((line) => {
        const last = merged[merged.length - 1];
        if (last && Math.abs(last.time - line.time) < 0.08) {
          if (!last.text.includes(line.text)) {
            last.text = `${last.text} / ${line.text}`;
          }
          return;
        }
        if (last && last.text === line.text && Math.abs(last.time - line.time) < 0.35) {
          return;
        }
        merged.push({ ...line });
      });
    return merged;
  };

  if (timedLines.length) {
    const mergedTimedLines = mergeTimedLines(timedLines);
    const lastTimestamp = mergedTimedLines[mergedTimedLines.length - 1]?.time || 0;
    if (durationSeconds > 0 && mergedTimedLines.length > 4 && lastTimestamp > durationSeconds * 1.35) {
      const scale = durationSeconds / lastTimestamp;
      return mergedTimedLines.map((line) => ({
        ...line,
        time: Math.max(0, Math.min(durationSeconds, Number((line.time * scale).toFixed(3))))
      }));
    }
    return mergedTimedLines;
  }

  if (!plainLines.length) {
    return [];
  }

  if (durationSeconds > 0 && plainLines.length > 1) {
    const step = durationSeconds / plainLines.length;
    return plainLines.map((line, index) => ({
      ...line,
      time: Number((index * step).toFixed(3))
    }));
  }

  return plainLines.map((line, index) => ({
    ...line,
    time: index
  }));
});

const activeLyricIndex = computed(() => {
  const lines = parsedLyrics.value;
  if (!lines.length) {
    return -1;
  }

  const firstTimedIndex = lines.findIndex((line) => typeof line.time === "number");
  if (firstTimedIndex < 0) {
    return 0;
  }

  const firstTimedLine = lines[firstTimedIndex];
  if (currentTime.value <= (firstTimedLine?.time ?? 0)) {
    return firstTimedIndex;
  }

  let activeIndex = firstTimedIndex;
  for (let index = firstTimedIndex + 1; index < lines.length; index += 1) {
    const line = lines[index];
    if (typeof line.time !== "number") {
      break;
    }

    if (currentTime.value >= line.time) {
      activeIndex = index;
    } else {
      break;
    }
  }

  return activeIndex;
});

const currentLine = computed(() => {
  if (activeLyricIndex.value >= 0) {
    return parsedLyrics.value[activeLyricIndex.value]?.text || "";
  }
  if (lyricLoading.value) {
    return "歌词加载中...";
  }
  if (currentTrack.value?.lyricSnippet) {
    return currentTrack.value.lyricSnippet;
  }
  return lyricError.value ? "歌词暂不可用" : "暂无歌词";
});

const previousLine = computed(() => {
  if (activeLyricIndex.value > 0) {
    return parsedLyrics.value[activeLyricIndex.value - 1]?.text || "";
  }
  return "";
});

const nextLine = computed(() => {
  if (activeLyricIndex.value >= 0 && activeLyricIndex.value < parsedLyrics.value.length - 1) {
    return parsedLyrics.value[activeLyricIndex.value + 1]?.text || "";
  }
  return "";
});

const coverUrl = computed(() => lyricsData.value?.coverUrl || currentTrack.value?.coverUrl || "");
const displayTitle = computed(() => lyricsData.value?.title || currentTrack.value?.title || "未选择歌曲");
const displayArtist = computed(() => lyricsData.value?.artistName || currentTrack.value?.artistName || "未知歌手");
const displayAlbum = computed(() => lyricsData.value?.albumName || currentTrack.value?.albumName || "");
const coverInitial = computed(() => {
  const normalized = String(displayTitle.value || "").trim();
  return normalized ? normalized.slice(0, 1) : "乐";
});
const coverAlt = computed(() => `${displayTitle.value}封面`);
const toggleLabel = computed(() => {
  if (lyricLoading.value) {
    return "准备中";
  }
  return isPlaying.value ? "暂停" : "播放";
});

const durationValue = computed(() =>
  Number.isFinite(duration.value) && duration.value > 0 ? duration.value : 0
);
const progressValue = computed(() => {
  if (!durationValue.value) {
    return 0;
  }
  return Math.min(currentTime.value, durationValue.value);
});
const progressPercent = computed(() =>
  durationValue.value ? `${(progressValue.value / durationValue.value) * 100}%` : "0%"
);
const currentTimeLabel = computed(() => formatTime(progressValue.value));
const durationLabel = computed(() => formatTime(durationValue.value));
const assetsReady = computed(() => Boolean(currentTrack.value) && !lyricLoading.value && Boolean(lyricsData.value));
const canControlPlayback = computed(() => Boolean(currentSrc.value));
const currentUserId = computed(() => resolveCurrentUserId());

const syncFromAudio = () => {
  if (!audioRef.value) {
    return;
  }

  updatePlaybackState({
    currentTime: audioRef.value.currentTime || 0,
    duration: Number.isFinite(audioRef.value.duration) ? audioRef.value.duration : 0,
    isPlaying: !audioRef.value.paused && !audioRef.value.ended
  });
};

const clampValue = (value, min, max) => Math.max(min, Math.min(max, value));

const seekTo = (nextTime) => {
  if (!audioRef.value || !Number.isFinite(nextTime)) {
    return;
  }
  const boundedTime = clampValue(nextTime, 0, durationValue.value || nextTime || 0);
  audioRef.value.currentTime = boundedTime;
  syncFromAudio();
};

const seekBy = (offset) => {
  const current = audioRef.value?.currentTime ?? progressValue.value;
  seekTo(current + offset);
};

const safePlay = async () => {
  if (!audioRef.value) {
    return false;
  }

  try {
    await audioRef.value.play();
    return true;
  } catch {
    updatePlaybackState({ isPlaying: false });
    return false;
  }
};

const updateMediaSessionMetadata = () => {
  if (!mediaSessionSupported) {
    return;
  }

  try {
    if (!currentTrack.value || !mediaMetadataSupported) {
      navigator.mediaSession.metadata = null;
      return;
    }

    const artwork = coverUrl.value
      ? [
          {
            src: coverUrl.value,
            sizes: "512x512",
            type: "image/jpeg"
          }
        ]
      : [];

    navigator.mediaSession.metadata = new window.MediaMetadata({
      title: displayTitle.value,
      artist: displayArtist.value,
      album: displayAlbum.value || "",
      artwork
    });
  } catch {
    // Ignore media session metadata failures on unsupported browsers.
  }
};

const updateMediaSessionPlaybackState = () => {
  if (!mediaSessionSupported) {
    return;
  }
  try {
    navigator.mediaSession.playbackState = isPlaying.value ? "playing" : "paused";
  } catch {
    // Ignore unsupported playbackState assignments.
  }
};

const updateMediaSessionPosition = () => {
  if (!mediaSessionSupported || typeof navigator.mediaSession.setPositionState !== "function") {
    return;
  }
  if (!durationValue.value || !Number.isFinite(progressValue.value)) {
    return;
  }
  try {
    navigator.mediaSession.setPositionState({
      duration: durationValue.value,
      playbackRate: audioRef.value?.playbackRate || 1,
      currentTime: Math.min(progressValue.value, durationValue.value)
    });
  } catch {
    // Some mobile browsers throw when position state is not supported yet.
  }
};

const setupMediaSession = () => {
  if (!mediaSessionSupported) {
    return;
  }

  const bindHandler = (action, handler) => {
    try {
      navigator.mediaSession.setActionHandler(action, handler);
    } catch {
      // Ignore unsupported action handlers.
    }
  };

  bindHandler("play", () => {
    void safePlay();
  });
  bindHandler("pause", () => {
    if (audioRef.value) {
      audioRef.value.pause();
    }
  });
  bindHandler("stop", () => {
    if (audioRef.value) {
      audioRef.value.pause();
    }
  });
  bindHandler("seekbackward", (details) => {
    seekBy(-(details?.seekOffset || 10));
  });
  bindHandler("seekforward", (details) => {
    seekBy(details?.seekOffset || 10);
  });
  bindHandler("seekto", (details) => {
    if (!Number.isFinite(details?.seekTime)) {
      return;
    }
    seekTo(details.seekTime);
  });
};

const clearMediaSession = () => {
  if (!mediaSessionSupported) {
    return;
  }
  try {
    navigator.mediaSession.metadata = null;
    navigator.mediaSession.playbackState = "paused";
  } catch {
    // Ignore cleanup failures.
  }
};

const loadLyrics = async () => {
  lyricError.value = "";
  lyricsData.value = null;

  if (!currentTrack.value) {
    lyricLoading.value = false;
    return false;
  }

  const cached = getCachedTrackAssets(currentTrack.value);
  if (cached) {
    lyricsData.value = cached;
    lyricLoading.value = false;
    return true;
  }

  lyricLoading.value = true;
  try {
    lyricsData.value = await prefetchTrackAssets(currentTrack.value);
    return Boolean(lyricsData.value);
  } catch (e) {
    lyricError.value = e.message;
    return false;
  } finally {
    lyricLoading.value = false;
  }
};

const normalizePlaybackSnapshot = (trackSnapshot, totalPlayedSeconds, durationSeconds) => {
  if (!trackSnapshot?.itemId || !currentUserId.value) {
    return null;
  }

  const roundedTotal = Math.max(0, Math.round(totalPlayedSeconds || 0));
  const roundedDuration = Math.max(0, Math.round(durationSeconds || 0));
  const deltaSeconds = Math.max(0, roundedTotal - reportedSeconds.value);

  if (roundedTotal <= 0 || deltaSeconds <= 0) {
    return null;
  }

  return {
    itemId: trackSnapshot.itemId,
    payload: {
      requestId: `web_player_${Date.now()}`,
      source: "global_player",
      playedSeconds: deltaSeconds,
      totalPlayedSeconds: roundedTotal,
      durationSeconds: roundedDuration || undefined,
      completed: false
    },
    roundedTotal
  };
};

const reportHistoryStart = async () => {
  const trackSnapshot = activeSessionTrack.value || currentTrack.value;
  if (!trackSnapshot?.itemId || !currentUserId.value || sessionStarted.value) {
    return;
  }

  sessionStarted.value = true;
  try {
    await reportPlayStart(currentUserId.value, trackSnapshot.itemId, {
      requestId: `web_player_${Date.now()}`,
      source: "global_player"
    });
  } catch {
    sessionStarted.value = false;
  }
};

const flushPlaybackProgress = async ({
  trackSnapshot = activeSessionTrack.value,
  totalPlayedSeconds,
  durationSeconds,
  completed = false,
  keepalive = false
} = {}) => {
  if (!trackSnapshot?.itemId || !currentUserId.value) {
    return;
  }

  const roundedTotal = Math.max(0, Math.round(totalPlayedSeconds || 0));
  const roundedDuration = Math.max(0, Math.round(durationSeconds || 0));
  const deltaSeconds = Math.max(0, roundedTotal - reportedSeconds.value);

  if (roundedTotal <= 0 || deltaSeconds <= 0) {
    if (completed) {
      sessionStarted.value = false;
    }
    return;
  }

  const payload = {
    requestId: `web_player_${Date.now()}`,
    source: "global_player",
    playedSeconds: deltaSeconds,
    totalPlayedSeconds: roundedTotal,
    durationSeconds: roundedDuration || undefined,
    completed
  };

  try {
    if (keepalive) {
      const token = getToken();
      if (!token) {
        return;
      }
      const response = window.fetch(
        `${API_BASE_URL}/history/users/${currentUserId.value}/items/${trackSnapshot.itemId}/play-complete`,
        {
          method: "POST",
          keepalive: true,
          mode: "cors",
          headers: {
            "Content-Type": "application/json",
            Authorization: token
          },
          body: JSON.stringify(payload)
        }
      );
      void response;
    } else {
      await reportPlayComplete(currentUserId.value, trackSnapshot.itemId, payload);
    }
    reportedSeconds.value = roundedTotal;
    if (completed) {
      sessionStarted.value = false;
    }
  } catch {
    if (completed) {
      sessionStarted.value = false;
    }
  }
};

const handleAudioPlay = () => {
  syncFromAudio();
  void reportHistoryStart();
};

const handleAudioPause = () => {
  syncFromAudio();
};

const togglePlayback = async () => {
  if (!audioRef.value || !canControlPlayback.value) {
    return;
  }

  if (audioRef.value.paused) {
    await safePlay();
    return;
  }

  audioRef.value.pause();
};

const handleSeek = (event) => {
  if (!audioRef.value || !canControlPlayback.value) {
    return;
  }

  const nextValue = Number(event.target.value || 0);
  seekTo(nextValue);
};

const handlePageHide = () => {
  const snapshot = normalizePlaybackSnapshot(
    activeSessionTrack.value,
    audioRef.value?.currentTime ?? progressValue.value,
    audioRef.value?.duration ?? durationValue.value
  );
  if (!snapshot) {
    return;
  }
  void flushPlaybackProgress({
    trackSnapshot: activeSessionTrack.value,
    totalPlayedSeconds: snapshot.payload.totalPlayedSeconds,
    durationSeconds: snapshot.payload.durationSeconds,
    completed: false,
    keepalive: true
  });
};

watch(playNonce, async () => {
  if (activeSessionTrack.value?.itemId) {
    await flushPlaybackProgress({
      trackSnapshot: activeSessionTrack.value,
      totalPlayedSeconds: audioRef.value?.currentTime ?? progressValue.value,
      durationSeconds: audioRef.value?.duration ?? durationValue.value,
      completed: false
    });
  }

  sessionStarted.value = false;
  reportedSeconds.value = 0;
  activeSessionTrack.value = null;

  if (!currentTrack.value || !currentSrc.value) {
    lyricsData.value = null;
    lyricError.value = "";
    lyricLoading.value = false;

    if (audioRef.value) {
      audioRef.value.pause();
      audioRef.value.removeAttribute("src");
      audioRef.value.load();
    }
    clearMediaSession();
    updatePlaybackState({ currentTime: 0, duration: 0, isPlaying: false });
    return;
  }

  await nextTick();
  if (!audioRef.value) {
    return;
  }

  audioRef.value.pause();
  audioRef.value.load();

  activeSessionTrack.value = currentTrack.value;
  reportedSeconds.value = 0;
  sessionStarted.value = false;
  await safePlay();
  void loadLyrics();
});

watch(
  () => [currentTrack.value?.itemId, displayTitle.value, displayArtist.value, displayAlbum.value, coverUrl.value],
  () => {
    updateMediaSessionMetadata();
  },
  { immediate: true }
);

watch(
  () => isPlaying.value,
  () => {
    updateMediaSessionPlaybackState();
  },
  { immediate: true }
);

watch(
  () => [progressValue.value, durationValue.value, isPlaying.value],
  () => {
    updateMediaSessionPosition();
  }
);

const handleEnded = async () => {
  await flushPlaybackProgress({
    totalPlayedSeconds: audioRef.value?.currentTime ?? progressValue.value,
    durationSeconds: audioRef.value?.duration ?? durationValue.value,
    completed: true
  });

  if (audioRef.value) {
    audioRef.value.currentTime = 0;
  }
  clearError();
  updatePlaybackState({ isPlaying: false, currentTime: 0 });
};

onMounted(() => {
  window.addEventListener("pagehide", handlePageHide);
  setupMediaSession();
  updateMediaSessionMetadata();
  updateMediaSessionPlaybackState();
});

onBeforeUnmount(() => {
  handlePageHide();
  window.removeEventListener("pagehide", handlePageHide);
  clearMediaSession();
});
</script>

<template>
  <section v-if="currentTrack" class="global-player">
    <audio
      ref="audioRef"
      :key="currentTrack.previewUrl"
      :src="currentSrc"
      class="global-player__audio"
      preload="metadata"
      playsinline
      webkit-playsinline="true"
      x5-playsinline="true"
      x-webkit-airplay="allow"
      @canplay="syncFromAudio"
      @ended="handleEnded"
      @loadedmetadata="syncFromAudio"
      @pause="handleAudioPause"
      @play="handleAudioPlay"
      @seeked="syncFromAudio"
      @seeking="syncFromAudio"
      @timeupdate="syncFromAudio"
    />

    <div class="global-player__shell">
      <div class="global-player__track">
        <div v-if="coverUrl" class="global-player__cover-wrap">
          <img class="global-player__cover" :src="coverUrl" :alt="coverAlt" />
        </div>
                <div v-else class="global-player__cover-placeholder">
          <span class="global-player__cover-initial">{{ coverInitial }}</span>
          <span v-if="coverStatusText" class="global-player__cover-status">{{ coverStatusText }}</span>
        </div>

        <div class="global-player__meta">
          <strong class="global-player__title">{{ displayTitle }}</strong>
          <span class="helper-text global-player__artist">{{ displayArtist }}</span>
          <span v-if="displayAlbum" class="helper-text global-player__album">{{ displayAlbum }}</span>
        </div>
      </div>

      <div class="global-player__stage">
        <div class="global-player__lyrics">
          <p class="global-player__lyric global-player__lyric--side">{{ previousLine || " " }}</p>
          <p class="global-player__lyric global-player__lyric--current">{{ currentLine }}</p>
          <p class="global-player__lyric global-player__lyric--side">{{ nextLine || " " }}</p>
        </div>

        <div class="global-player__timeline">
          <button
            class="global-player__icon-btn"
            type="button"
            :disabled="!canControlPlayback"
            @click="togglePlayback"
          >
            {{ toggleLabel }}
          </button>

          <span class="global-player__time">{{ currentTimeLabel }}</span>

          <input
            class="global-player__seek"
            type="range"
            min="0"
            :max="durationValue || 0"
            step="0.1"
            :value="progressValue"
            :style="{ '--progress': progressPercent }"
            :disabled="!canControlPlayback"
            @input="handleSeek"
          />

          <span class="global-player__time">{{ durationLabel }}</span>

          <button
            class="global-player__close-btn"
            type="button"
            :aria-label="'关闭播放器'"
            @click="clearTrack"
          >
            <span class="global-player__close-label">{{ "关闭" }}</span>
          </button>
        </div>
      </div>
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>
    <p v-else-if="lyricError" class="helper-text">{{ lyricError }}</p>
  </section>
</template>