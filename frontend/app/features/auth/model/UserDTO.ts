export interface UserDTO {
  id: number;
  username: string;
  displayName: string;
  email: string;
  role: "USER" | "ADMIN";
  active: boolean;
  avatarKey: string | null;
  createdAt: string;
  updatedAt: string;
}
