export interface UserDTO {
  id: number;
  username: string;
  displayName: string;
  email: string;
  role: "USER" | "ADMIN";
  active: boolean;
  // Null without an avatar; served by the backend (the storage key never leaves it).
  avatarUrl: string | null;
  createdAt: string;
  updatedAt: string;
}
