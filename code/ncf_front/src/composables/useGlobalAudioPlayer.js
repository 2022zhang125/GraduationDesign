import { computed, readonly, ref } from "vue";
import { fetchItemPreview, resolveApiAssetUrl } from "../api";

const PREVIEW_DEBOUNCE_MS = 800;

const currentTrack = ref(null);
const busy = ref(false);
const error = ref("");
const playNonce = ref(0);
const currentTime = ref(0);
const duration = ref(0);
const isPlaying = ref(false);
const lastPlayRequest = ref({ signature: "", issuedAt: 0 });

const normalizeItemId = (value) => {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
};

const normalizeTrackUrl = (track = {}) =>
  resolveApiAssetUrl(track.previewUrl || track.playUrl || track.musicUrl || "");

const normalizeTrackText = (value) => String(value || "").trim().toLowerCase();

const buildTrackIdentity = (input, metadata = {}) => {
  if (typeof input === "object" && input !== null) {
    return {
      itemId: normalizeItemId(input.itemId),
      src: normalizeTrackUrl(input),
      title: normalizeTrackText(input.title || input.name),
      artist: normalizeTrackText(input.artistName || input.artist)
    };
  }

  return {
    itemId: normalizeItemId(input),
    src: resolveApiAssetUrl(metadata.previewUrl || metadata.playUrl || metadata.musicUrl || ""),
    title: normalizeTrackText(metadata.title || metadata.name),
    artist: normalizeTrackText(metadata.artistName || metadata.artist)
  };
};

const isSameTrackIdentity = (left, right) => {
  if (!left || !right) {
    return false;
  }
  if (left.itemId && right.itemId) {
    return left.itemId === right.itemId;
  }
  if (left.src && right.src) {
    return left.src === right.src;
  }
  return Boolean(left.title && right.title && left.artist && right.artist && left.title === right.title && left.artist === right.artist);
};

const buildRequestSignature = (input, metadata = {}) => {
  const identity = buildTrackIdentity(input, metadata);
  return [identity.itemId ?? "", identity.src, identity.title, identity.artist].join("::");
};

const isCurrentTrackRequest = (input, metadata = {}) => {
  if (!currentTrack.value) {
    return false;
  }
  return isSameTrackIdentity(buildTrackIdentity(currentTrack.value), buildTrackIdentity(input, metadata));
};

const shouldDebounce = (signature) => {
  if (!signature) {
    return false;
  }
  const now = Date.now();
  const last = lastPlayRequest.value;
  return last.signature === signature && now - last.issuedAt < PREVIEW_DEBOUNCE_MS;
};

const markPlayRequest = (signature) => {
  lastPlayRequest.value = {
    signature,
    issuedAt: Date.now()
  };
};

const currentSrc = computed(() =>
  resolveApiAssetUrl(
    currentTrack.value?.previewUrl ||
      currentTrack.value?.playUrl ||
      currentTrack.value?.musicUrl ||
      ""
  )
);

const playItem = async (itemId, metadata = {}) => {
  if (!itemId || (isPlaying.value && isCurrentTrackRequest(itemId, metadata))) {
    return;
  }

  const signature = buildRequestSignature(itemId, metadata);
  if (busy.value || shouldDebounce(signature)) {
    return;
  }

  markPlayRequest(signature);
  busy.value = true;
  error.value = "";

  try {
    const preview = await fetchItemPreview(itemId);
    currentTrack.value = {
      ...preview,
      ...metadata,
      previewUrl: resolveApiAssetUrl(preview?.previewUrl || preview?.playUrl || preview?.musicUrl || ""),
      title: metadata.title || preview.title || metadata.name || "",
      artistName: metadata.artistName || preview.artistName || "",
      albumName: metadata.albumName || preview.albumName || "",
      coverUrl: resolveApiAssetUrl(metadata.coverUrl || preview?.coverUrl || ""),
      lyricSnippet: metadata.lyricSnippet || preview?.lyricSnippet || "",
      requestedAt: Date.now()
    };
    currentTime.value = 0;
    duration.value = 0;
    isPlaying.value = false;
    playNonce.value += 1;
  } catch (e) {
    error.value = e.message;
    throw e;
  } finally {
    busy.value = false;
  }
};

const clearTrack = () => {
  currentTrack.value = null;
  currentTime.value = 0;
  duration.value = 0;
  isPlaying.value = false;
  playNonce.value += 1;
};

const clearError = () => {
  error.value = "";
};

const playTrack = (track) => {
  if (!track || (isPlaying.value && isCurrentTrackRequest(track))) {
    return;
  }

  const signature = buildRequestSignature(track);
  if (busy.value || shouldDebounce(signature)) {
    return;
  }

  markPlayRequest(signature);
  error.value = "";
  currentTrack.value = {
    ...track,
    itemId: normalizeItemId(track.itemId),
    previewUrl: normalizeTrackUrl(track)
  };
  currentTime.value = 0;
  duration.value = 0;
  isPlaying.value = false;
  playNonce.value += 1;
};

const setTrackAssets = (assets = {}) => {
  if (!currentTrack.value || !assets) {
    return;
  }

  currentTrack.value = {
    ...currentTrack.value,
    coverUrl: resolveApiAssetUrl(assets.coverUrl || currentTrack.value.coverUrl || ""),
    lyricSnippet: assets.lyricsText || assets.lyricSnippet || currentTrack.value.lyricSnippet || ""
  };
};

const isSameAsCurrentTrack = (input, metadata = {}, options = {}) => {
  if (!currentTrack.value) {
    return false;
  }
  if (options.requirePlaying && !isPlaying.value) {
    return false;
  }
  return isCurrentTrackRequest(input, metadata);
};

const updatePlaybackState = (payload = {}) => {
  if (typeof payload.currentTime === "number" && Number.isFinite(payload.currentTime)) {
    currentTime.value = payload.currentTime;
  }
  if (typeof payload.duration === "number" && Number.isFinite(payload.duration)) {
    duration.value = payload.duration;
  }
  if (typeof payload.isPlaying === "boolean") {
    isPlaying.value = payload.isPlaying;
  }
};

export const useGlobalAudioPlayer = () => ({
  currentTrack: readonly(currentTrack),
  currentSrc,
  busy: readonly(busy),
  error: readonly(error),
  playNonce: readonly(playNonce),
  currentTime: readonly(currentTime),
  duration: readonly(duration),
  isPlaying: readonly(isPlaying),
  playItem,
  playTrack,
  isSameAsCurrentTrack,
  setTrackAssets,
  updatePlaybackState,
  clearTrack,
  clearError
});

