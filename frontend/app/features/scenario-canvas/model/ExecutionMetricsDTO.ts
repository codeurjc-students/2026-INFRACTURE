// What Docker reported for one node at one moment. null means there was no reading; it is not zero.
export interface MetricSampleDTO {
  id: number;
  componentKey: string;
  at: string;
  cpuPercent: number | null;
  memoryBytes: number | null;
}

// A node as it was frozen for the execution, with the limits its container ran with.
export interface MetricComponentDTO {
  key: string;
  name: string;
  cpuLimitPercent: number;
  memoryLimitBytes: number;
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
