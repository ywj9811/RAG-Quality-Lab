import { apiRequest } from "./client";

export interface AdminMe {
  username: string;
}

export const adminAuth = {
  me: () => apiRequest<AdminMe>("GET", "/api/admin/auth/me"),
  login: (username: string, password: string) =>
    apiRequest<AdminMe>("POST", "/api/admin/auth/login", { username, password }),
  logout: () => apiRequest<void>("POST", "/api/admin/auth/logout"),
};
