import { CircleAlertIcon } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Button } from "~/components/ui/button";
import { Spinner } from "~/components/ui/spinner";
import type { PageDTO } from "~/shared/model/PageDTO";

type PagedListProps<T extends { id: number }> = {
  initialPage: PageDTO<T>;
  loadPage: (page: number) => Promise<PageDTO<T>>;
  noun: string;
  renderItem: (item: T) => ReactNode;
};

export default function PagedList<T extends { id: number }>({
  initialPage,
  loadPage,
  noun,
  renderItem,
}: PagedListProps<T>) {
  const [items, setItems] = useState(initialPage);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const hasMore = items.page.number + 1 < items.page.totalPages;

  async function loadMore() {
    setLoading(true);
    setError(false);
    try {
      const nextPage = await loadPage(items.page.number + 1);
      setItems((current) => {
        // An item created meanwhile shifts the pages, so skip the ones already shown.
        const shown = new Set(current.content.map((item) => item.id));
        return {
          ...nextPage,
          content: [
            ...current.content,
            ...nextPage.content.filter((item) => !shown.has(item.id)),
          ],
        };
      });
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <ul className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,340px),1fr))] gap-2">
        {items.content.map((item) => (
          <li key={item.id}>{renderItem(item)}</li>
        ))}
      </ul>
      {error && (
        <Alert variant="destructive" className="enter-soft mt-6 max-w-xl">
          <CircleAlertIcon />
          <AlertTitle>{`Could not load more ${noun}`}</AlertTitle>
          <AlertDescription>
            {`The ${noun} already shown are still here. Try again.`}
          </AlertDescription>
        </Alert>
      )}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-3 pt-6">
        {hasMore && (
          <Button
            type="button"
            variant="outline"
            size="lg"
            onClick={loadMore}
            disabled={loading}
            className="w-full px-4 sm:w-auto"
          >
            {loading && <Spinner data-icon="inline-start" />}
            {loading ? "Loading…" : error ? "Try again" : "More results"}
          </Button>
        )}
        <p
          role="status"
          className="type-small text-muted-foreground tabular-nums"
        >
          {loading
            ? `Loading more ${noun}…`
            : `Showing ${items.content.length} of ${items.page.totalElements}`}
        </p>
      </div>
    </>
  );
}
