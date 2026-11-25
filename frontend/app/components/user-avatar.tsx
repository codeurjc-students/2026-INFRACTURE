import { cn } from "cn";
import { useState } from "react";

export function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join("");
}

type UserAvatarProps = {
  user: { displayName: string; avatarUrl: string | null };
  className?: string;
};

// The stored image is square; the circle is only how it is shown. Without an image, or when it fails
// to load, the initials take its place. It is decorative: the name is always next to it or in a label.
export default function UserAvatar({ user, className }: UserAvatarProps) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const url = user.avatarUrl !== failedUrl ? user.avatarUrl : null;

  return (
    <span
      className={cn(
        "grid shrink-0 place-items-center overflow-hidden rounded-full border bg-card font-semibold text-foreground",
        className,
      )}
    >
      {url ? (
        <img
          src={url}
          alt=""
          className="size-full object-cover"
          onError={() => setFailedUrl(url)}
        />
      ) : (
        <span aria-hidden="true">{initials(user.displayName)}</span>
      )}
    </span>
  );
}
