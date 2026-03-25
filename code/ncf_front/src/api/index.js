import http, {
  API_BASE_URL,
  API_ORIGIN,
  clearToken,
  getAuthName,
  getAuthUserId,
  getToken,
  resolveApiAssetUrl,
  setAuthName,
  setAuthUserId,
  setToken
} from "./http";

export {
  API_BASE_URL,
  API_ORIGIN,
  clearToken,
  getAuthName,
  getAuthUserId,
  getToken,
  resolveApiAssetUrl,
  setAuthName,
  setAuthUserId,
  setToken
};

export const login = (payload) => http.post("/auth/login", payload);

export const register = (payload) => http.post("/auth/register", payload);

export const fetchStats = () => http.get("/stats/overview");

export const fetchUsers = (page = 1, size = 10) =>
  http.get("/users", { params: { page, size } });

export const fetchItems = ({ genre, page = 1, size = 10 } = {}) =>
  http.get("/items", { params: { genre, page, size } });

export const fetchAllItems = async ({ genre, size = 200 } = {}) => {
  const firstPage = await fetchItems({ genre, page: 1, size });
  const total = Number(firstPage?.total || 0);
  const list = Array.isArray(firstPage?.list) ? [...firstPage.list] : [];

  if (list.length >= total || total <= size) {
    return list;
  }

  const totalPages = Math.ceil(total / size);
  for (let page = 2; page <= totalPages; page += 1) {
    const pageData = await fetchItems({ genre, page, size });
    if (Array.isArray(pageData?.list)) {
      list.push(...pageData.list);
    }
  }

  return list;
};

export const searchMusic = (query, { limit, level, source } = {}) =>
  http.get("/music/search", { params: { query, limit, level, source } });

export const fetchMusicPlaybackDetail = ({ query, n, level, source, itemId, title, album, artist } = {}) =>
  http.get("/music/detail", {
    params: { query, n, level, source, itemId, title, album, artist }
  });

export const fetchLyricsLookup = ({ itemId, title, album, artist } = {}) =>
  http.get("/lyrics/lookup", {
    params: {
      itemId,
      title,
      album,
      artist
    }
  });

export const fetchRecommendations = (userId, limit = 10) =>
  http.get(`/recommendations/users/${userId}`, { params: { limit } });

export const fetchExplanation = (userId, itemId, limit = 10) =>
  http.get(`/recommendations/users/${userId}/items/${itemId}/explanation`, {
    params: { limit }
  });

export const fetchItemPreview = (itemId) => http.get(`/media/items/${itemId}/preview`);

export const fetchFavoriteItems = (userId) => http.get(`/favorites/users/${userId}`);

export const isFavoriteItem = (userId, itemId) =>
  http.get(`/favorites/users/${userId}/items/${itemId}/exists`);

export const addFavoriteItem = (userId, itemId) =>
  http.post(`/favorites/users/${userId}/items/${itemId}`);

export const removeFavoriteItem = (userId, itemId) =>
  http.delete(`/favorites/users/${userId}/items/${itemId}`);

export const fetchFollowingUsers = (userId) => http.get(`/follows/users/${userId}`);

export const isFollowingUser = (userId, targetUserId) =>
  http.get(`/follows/users/${userId}/targets/${targetUserId}/exists`);

export const addFollowingUser = (userId, targetUserId) =>
  http.post(`/follows/users/${userId}/targets/${targetUserId}`);

export const removeFollowingUser = (userId, targetUserId) =>
  http.delete(`/follows/users/${userId}/targets/${targetUserId}`);

export const fetchFeedbackTickets = (userId, page = 1, size = 10, status = "") =>
  http.get(`/feedback/users/${userId}`, { params: { page, size, status } });

export const createFeedbackTicket = (userId, payload) =>
  http.post(`/feedback/users/${userId}`, payload);

export const closeFeedbackTicket = (userId, ticketId) =>
  http.delete(`/feedback/users/${userId}/tickets/${ticketId}`);

export const fetchListeningHistory = (userId, page = 1, size = 10) =>
  http.get(`/history/users/${userId}`, { params: { page, size } });

export const fetchHistoryFeed = ({ userId, page = 1, size = 10 } = {}) =>
  http.get("/history", { params: { userId, page, size } });

export const reportPlayStart = (userId, itemId, payload = {}) =>
  http.post(`/history/users/${userId}/items/${itemId}/play-start`, payload);

export const reportPlayComplete = (userId, itemId, payload = {}) =>
  http.post(`/history/users/${userId}/items/${itemId}/play-complete`, payload);


