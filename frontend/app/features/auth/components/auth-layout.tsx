import type { ReactNode } from "react";
import Logo from "~/components/logo";

type AuthLayoutProps = {
  headline: [string, string];
  title: string;
  description: string;
  children: ReactNode;
};

export default function AuthLayout({
  headline,
  title,
  description,
  children,
}: AuthLayoutProps) {
  return (
    <div className="theme-dark min-h-svh bg-background text-foreground">
      <meta name="theme-color" content="#0d1114" />
      <div className="mx-auto flex min-h-svh w-full max-w-6xl flex-col gap-6 px-6 py-6 sm:px-10 lg:px-16 [@media(max-height:720px)]:py-4">
        <header className="flex h-10 items-center">
          <Logo className="h-6 w-auto" />
        </header>
        <div className="flex flex-1 flex-wrap items-center gap-x-12 gap-y-16">
          <section className="@container flex grow basis-md flex-col gap-6">
            <p className="type-hero">
              {headline[0]}
              <br />
              {headline[1]}
            </p>
            <p className="type-title max-w-md text-muted-foreground">
              Design a distributed system, run it on Docker and inject faults to
              see how every component reacts.
            </p>
          </section>
          <main className="grow basis-md">
            <div className="flex w-full max-w-sm flex-col gap-6">
              <div className="flex flex-col gap-2">
                <h1 className="type-display">{title}</h1>
                <p className="type-body text-muted-foreground">{description}</p>
              </div>
              {children}
            </div>
          </main>
        </div>
      </div>
    </div>
  );
}
