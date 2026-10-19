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
  return apiFetch<UserDTO>("/api/v1/users/", {
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
