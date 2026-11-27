// What Docker and the node's counters reported at one moment. null or an absent counter means no reading.
export interface MetricSampleDTO {
  id: number;
  componentKey: string;
  at: string;
  cpuPercent: number | null;
  memoryBytes: number | null;
  counters: Record<string, number>;
}

// A node as it was frozen for the execution, with the limits its container ran with and its declared counters.
export interface MetricComponentDTO {
  key: string;
  name: string;
  cpuLimitPercent: number;
  memoryLimitBytes: number;
  counters: string[];
}

// One page of the series of every node of an execution, with where the values come from and how to read
// them.
export interface ExecutionMetricsDTO {
  source: "DOCKER_STATS";
  cpuUnit: "PERCENT_OF_ONE_CORE";
  memoryUnit: "BYTES";
  intervalSeconds: number;
  // More samples may still come.
  capturing: boolean;
  hasMore: boolean;
  // The cursor of the next read.
  nextAfter: number;
  components: MetricComponentDTO[];
  samples: MetricSampleDTO[];
}
