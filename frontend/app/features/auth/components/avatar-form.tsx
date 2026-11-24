import { useEffect, useId, useLayoutEffect, useRef, useState, type RefObject } from "react";
import Cropper from "react-easy-crop";
import { useFetcher } from "react-router";
import { Button } from "~/components/ui/button";
import { Spinner } from "~/components/ui/spinner";
import UserAvatar from "~/components/user-avatar";
import { cropToSquare, type CropArea } from "../crop-image";
import { FormResult, type AccountFormResult } from "./account-form";

// The server takes PNG and JPEG. WebP is fine too: the browser turns the crop into a PNG before sending it.
const ACCEPTED_TYPES = ["image/png", "image/jpeg", "image/webp"];

type AvatarFormProps = {
  user: { displayName: string; avatarUrl: string | null };
};

// Card of the account page to upload, crop or remove the profile photo. The photo is chosen and cropped
// in the browser; only the cropped square is sent. A failed upload keeps the current photo.
export default function AvatarForm({ user }: AvatarFormProps) {
  const fetcher = useFetcher<AccountFormResult>();
  const titleId = useId();
  const helpId = useId();
  const zoomId = useId();
  const fileRef = useRef<HTMLInputElement>(null);
  const imageRef = useRef<RefObject<HTMLImageElement> | null>(null);
  const resultRef = useRef<HTMLDivElement>(null);
  const [source, setSource] = useState<string | null>(null);
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [area, setArea] = useState<CropArea | null>(null);
  const [intent, setIntent] = useState<"avatar" | "remove-avatar">("avatar");
  const [localError, setLocalError] = useState<string | null>(null);
  const [dismissed, setDismissed] = useState<AccountFormResult>();
  const pending = fetcher.state !== "idle";
  const serverResult = pending || fetcher.data === dismissed ? undefined : fetcher.data;
  const result: AccountFormResult | undefined = localError ? { error: localError } : serverResult;

  // The object URL holds the whole file in memory until it is released.
  useEffect(() => {
    if (!source) {
      return;
    }
    return () => URL.revokeObjectURL(source);
  }, [source]);

  // A saved photo closes the cropper. A failed one keeps it open to try again.
  useLayoutEffect(() => {
    if (!serverResult) {
      return;
    }
    if ("saved" in serverResult) {
      setSource(null);
    }
    resultRef.current?.focus();
  }, [serverResult]);

  function choose(file: File | undefined) {
    if (!file) {
      return;
    }
    setDismissed(fetcher.data);
    if (!ACCEPTED_TYPES.includes(file.type)) {
      setLocalError("Choose a PNG, JPEG or WebP image.");
      return;
    }
    setLocalError(null);
    setCrop({ x: 0, y: 0 });
    setZoom(1);
    setArea(null);
    setSource(URL.createObjectURL(file));
  }

  // Closing the cropper also closes the message of its last attempt.
  function cancel() {
    setSource(null);
    setLocalError(null);
    setDismissed(fetcher.data);
  }

  async function save() {
    const image = imageRef.current?.current;
    if (!image || !area) {
      return;
    }
    try {
      const avatar = await cropToSquare(image, area);
      const form = new FormData();
      form.append("intent", "avatar");
      form.append("avatar", avatar, "avatar.png");
      setLocalError(null);
      setIntent("avatar");
      await fetcher.submit(form, { method: "post", encType: "multipart/form-data" });
    } catch {
      setLocalError("This browser could not prepare the photo. Try another browser.");
    }
  }

  function remove() {
    setLocalError(null);
    setIntent("remove-avatar");
    fetcher.submit({ intent: "remove-avatar" }, { method: "post" });
  }

  return (
    <section
      aria-labelledby={titleId}
      className="flex flex-col gap-5 rounded-xl border bg-card p-5 sm:p-6"
    >
      <div className="flex flex-col gap-1">
        <h2 id={titleId} className="type-title">
          Profile photo
        </h2>
        <p className="type-body text-muted-foreground">
          A PNG, JPEG or WebP image. You choose the part that is shown.
        </p>
      </div>
      {result && (
        <FormResult
          ref={resultRef}
          result={result}
          savedMessage={intent === "avatar" ? "Photo saved" : "Photo removed"}
        />
      )}
      <input
        ref={fileRef}
        type="file"
        accept={ACCEPTED_TYPES.join(",")}
        hidden
        onChange={(event) => {
          choose(event.target.files?.[0]);
          event.target.value = "";
        }}
      />
      {source ? (
        <div className="flex flex-col gap-4">
          <div className="relative aspect-square w-full max-w-80 overflow-hidden rounded-lg bg-muted">
            <Cropper
              image={source}
              crop={crop}
              zoom={zoom}
              aspect={1}
              cropShape="round"
              showGrid={false}
              onCropChange={setCrop}
              onZoomChange={setZoom}
              onCropComplete={(_, pixels) => setArea(pixels)}
              setImageRef={(ref) => {
                imageRef.current = ref;
              }}
              mediaProps={{
                onError: () => {
                  setSource(null);
                  setLocalError("This image could not be read. Choose another one.");
                },
              }}
              cropperProps={{
                role: "group",
                "aria-label": "Visible part of the photo",
                "aria-describedby": helpId,
              }}
            />
          </div>
          <p id={helpId} className="type-body text-muted-foreground">
            Drag the photo, or focus it and use the arrow keys, to choose what the circle shows.
          </p>
          <div className="flex max-w-80 items-center gap-3">
            <label htmlFor={zoomId} className="type-body">
              Zoom
            </label>
            <input
              id={zoomId}
              type="range"
              min={1}
              max={3}
              step={0.05}
              value={zoom}
              onChange={(event) => setZoom(Number(event.target.value))}
              className="w-full accent-primary"
            />
          </div>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Button
              size="lg"
              disabled={pending || !area}
              onClick={save}
              className="h-11 px-4 sm:h-10"
            >
              {pending && <Spinner data-icon="inline-start" />}
              {pending ? "Saving…" : "Save photo"}
            </Button>
            <Button
              variant="outline"
              size="lg"
              disabled={pending}
              onClick={cancel}
              className="h-11 px-4 sm:h-10"
            >
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap items-center gap-4">
          <UserAvatar user={user} className="size-20 type-title" />
          <div className="flex flex-col gap-3 sm:flex-row">
            <Button
              variant="outline"
              size="lg"
              disabled={pending}
              onClick={() => fileRef.current?.click()}
              className="h-11 px-4 sm:h-10"
            >
              {user.avatarUrl ? "Change photo" : "Upload photo"}
            </Button>
            {user.avatarUrl && (
              <Button
                variant="ghost"
                size="lg"
                disabled={pending}
                onClick={remove}
                className="h-11 px-4 sm:h-10"
              >
                {pending && <Spinner data-icon="inline-start" />}
                {pending ? "Removing…" : "Remove photo"}
              </Button>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
