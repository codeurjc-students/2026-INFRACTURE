import { CircleAlertIcon, CircleCheckIcon } from "lucide-react";
import { useId, useLayoutEffect, useRef, type ReactNode, type Ref } from "react";
import { useFetcher } from "react-router";
import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Button } from "~/components/ui/button";
import { FieldGroup } from "~/components/ui/field";
import { Spinner } from "~/components/ui/spinner";

export type AccountFormResult = { saved: true } | { error: string };

type AccountFormProps = {
  intent: "profile" | "email" | "password";
  title: string;
  description?: string;
  submitLabel: string;
  pendingLabel: string;
  savedMessage: string;
  children: ReactNode;
};

// The outcome of a save. It takes the focus, so keyboard and screen reader users hear it.
export function FormResult({
  ref,
  result,
  savedMessage,
}: {
  ref: Ref<HTMLDivElement>;
  result: AccountFormResult;
  savedMessage: string;
}) {
  return "error" in result ? (
    <Alert
      ref={ref}
      tabIndex={-1}
      variant="destructive"
      className="enter-soft scroll-mt-20"
    >
      <CircleAlertIcon />
      <AlertTitle>Could not save the changes</AlertTitle>
      <AlertDescription>{result.error}</AlertDescription>
    </Alert>
  ) : (
    <Alert
      ref={ref}
      tabIndex={-1}
      role="status"
      className="enter-soft scroll-mt-20"
    >
      <CircleCheckIcon className="text-healthy-text" />
      <AlertTitle>{savedMessage}</AlertTitle>
    </Alert>
  );
}

// One card of the account page. Each card saves on its own and shows only its own result.
export default function AccountForm({
  intent,
  title,
  description,
  submitLabel,
  pendingLabel,
  savedMessage,
  children,
}: AccountFormProps) {
  const fetcher = useFetcher<AccountFormResult>();
  const titleId = useId();
  const formRef = useRef<HTMLFormElement>(null);
  const resultRef = useRef<HTMLDivElement>(null);
  const pending = fetcher.state !== "idle";
  const result = pending ? undefined : fetcher.data;

  // A save shows the stored values. An error keeps what was typed, except the passwords.
  // Before the paint, so the result never appears next to a password that is still filled in.
  useLayoutEffect(() => {
    if (!result) {
      return;
    }
    if ("saved" in result) {
      formRef.current?.reset();
    } else {
      formRef.current
        ?.querySelectorAll<HTMLInputElement>("input[name$='Password']")
        .forEach((input) => {
          input.value = "";
        });
    }
    resultRef.current?.focus();
  }, [result]);

  return (
    <section
      aria-labelledby={titleId}
      className="flex flex-col gap-5 rounded-xl border bg-card p-5 sm:p-6"
    >
      <div className="flex flex-col gap-1">
        <h2 id={titleId} className="type-title">
          {title}
        </h2>
        {description && (
          <p className="type-body text-muted-foreground">{description}</p>
        )}
      </div>
      {result && (
        <FormResult ref={resultRef} result={result} savedMessage={savedMessage} />
      )}
      <fetcher.Form ref={formRef} method="post" noValidate>
        <input type="hidden" name="intent" value={intent} />
        <FieldGroup className="gap-5">
          {children}
          <Button
            type="submit"
            size="lg"
            disabled={pending}
            className="h-11 w-full px-4 sm:h-10 sm:w-auto sm:self-start"
          >
            {pending && <Spinner data-icon="inline-start" />}
            {pending ? pendingLabel : submitLabel}
          </Button>
        </FieldGroup>
      </fetcher.Form>
    </section>
  );
}
