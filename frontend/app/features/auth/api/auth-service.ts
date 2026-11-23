import { apiFetch } from "~/shared/api/api-fetch";
import type { RegisterUserDTO } from "../model/RegisterUserDTO";
import type { UserDTO } from "../model/UserDTO";

export async function login(email: string, password: string): Promise<void> {
  await apiFetch("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function register(user: RegisterUserDTO): Promise<UserDTO> {
  return apiFetch<UserDTO>("/api/v1/users", {
    method: "POST",
    body: JSON.stringify(user),
  });
}

export function logout(): Promise<void> {
  return apiFetch("/api/v1/auth/logout", { method: "POST" });
}

export function getCurrentUser(): Promise<UserDTO> {
  return apiFetch<UserDTO>("/api/v1/users/me");
}

export function updateProfile(profile: {
  displayName?: string;
  username?: string;
}): Promise<UserDTO> {
  return apiFetch<UserDTO>("/api/v1/users/me", {
    method: "PATCH",
    body: JSON.stringify(profile),
  });
}

export function changeEmail(change: {
  email: string;
  currentPassword: string;
}): Promise<UserDTO> {
  return apiFetch<UserDTO>("/api/v1/users/me/email", {
    method: "PUT",
    body: JSON.stringify(change),
  });
}

export function changePassword(change: {
  currentPassword: string;
  newPassword: string;
}): Promise<UserDTO> {
  return apiFetch<UserDTO>("/api/v1/users/me/password", {
    method: "PUT",
    body: JSON.stringify(change),
  });
}
