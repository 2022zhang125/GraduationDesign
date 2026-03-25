import { getAuthName, getAuthUserId } from "../api";

export const resolveCurrentUsername = () => String(getAuthName() || "").trim();

export const resolveCurrentUserId = () => {
  const raw = getAuthUserId();
  const parsed = Number(raw);
  return Number.isInteger(parsed) && parsed > 0 ? String(parsed) : "";
};

export const isAdminUser = (username = resolveCurrentUsername()) =>
  username.toLowerCase() === "admin";
