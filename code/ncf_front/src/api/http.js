import axios from "axios";

const TOKEN_KEY = "ncf_token";
const AUTH_NAME_KEY = "ncf_auth_name";
const AUTH_USER_ID_KEY = "ncf_auth_user_id";
const DEFAULT_API_ORIGIN = "https://ncf.back.believesun.cn";

const trimTrailingSlash = (value) => String(value || "").replace(/\/+$/, "");
const trimLeadingSlash = (value) => String(value || "").replace(/^\/+/, "");

export const API_ORIGIN = trimTrailingSlash(
  import.meta.env.VITE_API_ORIGIN || DEFAULT_API_ORIGIN
);
export const API_BASE_URL = `${API_ORIGIN}/api`;

export const resolveApiAssetUrl = (value) => {
  if (!value) {
    return "";
  }

  const normalized = String(value).trim();
  if (!normalized) {
    return "";
  }

  if (/^https?:\/\//i.test(normalized) || normalized.startsWith("data:")) {
    return normalized;
  }

  if (normalized.startsWith("/api/")) {
    return `${API_ORIGIN}${normalized}`;
  }

  if (normalized.startsWith("api/")) {
    return `${API_ORIGIN}/${trimLeadingSlash(normalized)}`;
  }

  if (normalized.startsWith("/")) {
    return `${API_ORIGIN}${normalized}`;
  }

  return normalized;
};

const getCurrentRoutePath = () => {
  if (typeof window === "undefined") {
    return "/";
  }

  const hash = window.location.hash || "";
  if (hash.startsWith("#/")) {
    return hash.slice(1);
  }
  return `${window.location.pathname}${window.location.search}`;
};

const buildLoginRedirectUrl = (redirect) => {
  const encoded = encodeURIComponent(redirect || "/");
  return `/#/login?redirect=${encoded}`;
};

export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const getAuthName = () => localStorage.getItem(AUTH_NAME_KEY);
export const getAuthUserId = () => localStorage.getItem(AUTH_USER_ID_KEY);

export const setToken = (value) => {
  localStorage.setItem(TOKEN_KEY, value);
};

export const setAuthName = (value) => {
  if (!value) {
    localStorage.removeItem(AUTH_NAME_KEY);
    return;
  }
  localStorage.setItem(AUTH_NAME_KEY, value);
};

export const setAuthUserId = (value) => {
  if (!value && value !== 0) {
    localStorage.removeItem(AUTH_USER_ID_KEY);
    return;
  }
  localStorage.setItem(AUTH_USER_ID_KEY, String(value));
};

export const clearToken = () => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(AUTH_NAME_KEY);
  localStorage.removeItem(AUTH_USER_ID_KEY);
};

const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000
});

http.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = token;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const payload = response.data;
    if (payload && Object.prototype.hasOwnProperty.call(payload, "code")) {
      if (payload.code === 0) {
        return payload.data;
      }
      return Promise.reject(new Error(payload.message || "请求失败"));
    }
    return payload;
  },
  (error) => {
    if (error?.response?.status === 401) {
      clearToken();
      const currentRoutePath = getCurrentRoutePath();
      const isPublic = currentRoutePath.startsWith("/login") || currentRoutePath.startsWith("/register");
      if (!isPublic) {
        window.location.href = buildLoginRedirectUrl(currentRoutePath);
      }
    }

    const message =
      error?.response?.data?.message || error.message || "网络请求失败";
    return Promise.reject(new Error(message));
  }
);

export default http;
