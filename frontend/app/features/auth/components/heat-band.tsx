const COLUMNS = 40;
const CALM = [0, 0, 1, 0, 0, 0, 1, 0, 0];
const LEVEL_CLASS = [
  "bg-accent",
  "bg-heat-1",
  "bg-heat-2",
  "bg-heat-3",
  "bg-heat-4",
];

function sampleRow(
  offset: number,
  rise?: { from: number; warm: number; hot: number; peak: number },
) {
  return Array.from({ length: COLUMNS }, (_, index) => {
    const calm = CALM[(index * 7 + offset) % CALM.length];
    if (!rise || index < rise.from) return calm;
    return Math.max(calm, index >= rise.hot ? rise.peak : rise.warm);
  });
}

const ROWS = [
  { name: "Traffic pulse", levels: sampleRow(1) },
  {
    name: "Checkout API",
    levels: sampleRow(2, { from: 22, warm: 2, hot: 30, peak: 3 }),
  },
  {
    name: "Session cache",
    levels: sampleRow(3, { from: 22, warm: 3, hot: 30, peak: 4 }),
  },
  {
    name: "Orders store",
    levels: sampleRow(4, { from: 30, warm: 1, hot: 30, peak: 2 }),
  },
];

export default function HeatBand() {
  return (
    <figure className="m-0 flex flex-col gap-2.5">
      <figcaption className="flex flex-wrap justify-between gap-x-6 gap-y-1 type-caption text-muted-foreground">
        <span>Example: p95 latency while a cache goes down</span>
        <span className="inline-flex items-center gap-1.5" aria-hidden="true">
          cold
          {LEVEL_CLASS.map((level) => (
            <i
              key={level}
              className={`block h-2.5 w-3.5 rounded-[2px] ${level}`}
            />
          ))}
          hot
        </span>
      </figcaption>
      <div
        aria-hidden="true"
        className="grid grid-cols-[7rem_repeat(40,minmax(0,1fr))] items-center gap-0.5"
      >
        {ROWS.map((row) => [
          <span
            key={row.name}
            className="truncate pe-2 type-caption text-muted-foreground"
          >
            {row.name}
          </span>,
          ...row.levels.map((level, index) => (
            <i
              key={`${row.name}-${index}`}
              className={`block h-3.5 rounded-[2px] ${LEVEL_CLASS[level]}`}
            />
          )),
        ])}
      </div>
    </figure>
  );
}
