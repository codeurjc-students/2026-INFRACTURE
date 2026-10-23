import { HttpError } from "~/shared/api/api-fetch";

export const NAME_MAX_LENGTH = 30;
export const DESCRIPTION_MAX_LENGTH = 280;

export type DetailsFormResult = {
  error?: string;
  nameError?: string;
};

export function readDetailsForm(
  form: FormData,
  subject: "project" | "scenario",
) {
  const details = {
    name: String(form.get("name") ?? "").trim(),
    description: String(form.get("description") ?? "").trim(),
  };
  return {
    details,
    nameError: details.name ? undefined : `Enter a name for the ${subject}.`,
  };
}

// The backend explains validation errors (400); anything else gets a general message.
export function detailsErrorMessage(error: unknown) {
  return error instanceof HttpError && error.status === 400
    ? error.message
    : "Something went wrong. Try again in a moment.";
}

const dateFormat = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
  year: "numeric",
});
const dateTimeFormat = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

export function formatDate(value: string) {
  return dateFormat.format(new Date(value));
}

export function formatDateTime(value: string) {
  return dateTimeFormat.format(new Date(value));
}

const relativeFormat = new Intl.RelativeTimeFormat("en-GB", {
  numeric: "auto",
});

// Recent changes read as "2 minutes ago" or "yesterday"; older ones as a date.
export function formatUpdated(value: string, now = Date.now()) {
  const minutes = Math.round((new Date(value).getTime() - now) / 60_000);
  if (minutes > -1) return "just now";
  if (minutes > -60) return relativeFormat.format(minutes, "minute");
  const hours = Math.round(minutes / 60);
  if (hours > -24) return relativeFormat.format(hours, "hour");
  const days = Math.round(hours / 24);
  if (days > -7) return relativeFormat.format(days, "day");
  return formatDate(value);
}
