import { fetchLyricsLookup, resolveApiAssetUrl } from "../api";

const CACHE_LIMIT = 160;
const assetCache = new Map();

const normalizeText = (value) => String(value || "").trim();

const normalizeTrack = (track = {}) => ({
  itemId: Number.isInteger(Number(track.itemId)) && Number(track.itemId) > 0 ? Number(track.itemId) : undefined,
  title: normalizeText(track.title || track.name),
  album: normalizeText(track.albumName || track.album),
  artist: normalizeText(track.artistName || track.artist)
});

const buildAssetKey = (track = {}) => {
  const normalized = normalizeTrack(track);
  return [
    normalized.itemId ?? "",
    normalized.title.toLowerCase(),
    normalized.album.toLowerCase(),
    normalized.artist.toLowerCase()
  ].join("::");
};

const trimCache = () => {
  while (assetCache.size > CACHE_LIMIT) {
    const oldestKey = assetCache.keys().next().value;
    assetCache.delete(oldestKey);
  }
};

const preloadCover = (coverUrl) =>
  new Promise((resolve) => {
    const resolvedUrl = resolveApiAssetUrl(coverUrl);
    if (!resolvedUrl || typeof Image === "undefined") {
      resolve(false);
      return;
    }

    const image = new Image();
    image.decoding = "async";
    image.referrerPolicy = "no-referrer";
    image.onload = () => resolve(true);
    image.onerror = () => resolve(false);
    image.src = resolvedUrl;
  });

export const getCachedTrackAssets = (track) => {
  const key = buildAssetKey(track);
  if (!key) {
    return null;
  }

  const cached = assetCache.get(key);
  return cached?.data || null;
};

export const prefetchTrackAssets = async (track) => {
  const normalized = normalizeTrack(track);
  if (!normalized.title && !normalized.artist) {
    return null;
  }

  const key = buildAssetKey(normalized);
  const existing = assetCache.get(key);
  if (existing?.data) {
    return existing.data;
  }
  if (existing?.promise) {
    return existing.promise;
  }

  const promise = (async () => {
    const data = await fetchLyricsLookup({
      itemId: normalized.itemId,
      title: normalized.title || undefined,
      album: normalized.album || undefined,
      artist: normalized.artist || undefined
    });
    const resolved = data
      ? {
          ...data,
          coverUrl: resolveApiAssetUrl(data.coverUrl || "")
        }
      : data;
    if (resolved?.coverUrl) {
      await preloadCover(resolved.coverUrl);
    }
    assetCache.set(key, { data: resolved, promise: null, cachedAt: Date.now() });
    trimCache();
    return resolved;
  })().catch((error) => {
    assetCache.delete(key);
    throw error;
  });

  assetCache.set(key, { data: null, promise, cachedAt: Date.now() });
  trimCache();
  return promise;
};

export const scheduleTrackAssetPrefetch = (tracks, { concurrency = 3 } = {}) => {
  const queue = Array.isArray(tracks) ? tracks.filter(Boolean) : [];
  if (!queue.length) {
    return;
  }

  const run = async () => {
    let index = 0;
    const workers = Array.from({ length: Math.min(concurrency, queue.length) }, async () => {
      while (index < queue.length) {
        const currentIndex = index;
        index += 1;
        try {
          await prefetchTrackAssets(queue[currentIndex]);
        } catch {
          // Ignore single-track prefetch failures. Playback path will retry on demand.
        }
      }
    });
    await Promise.all(workers);
  };

  if (typeof window !== "undefined" && typeof window.requestIdleCallback === "function") {
    window.requestIdleCallback(() => {
      void run();
    }, { timeout: 1200 });
    return;
  }

  setTimeout(() => {
    void run();
  }, 80);
};
