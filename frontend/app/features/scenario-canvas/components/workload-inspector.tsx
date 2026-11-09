import { DicesIcon, GaugeIcon, Trash2Icon, XIcon } from "lucide-react";
import { useId, useState, type Dispatch } from "react";
import { Button } from "~/components/ui/button";
import { Field, FieldDescription, FieldLabel } from "~/components/ui/field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "~/components/ui/select";
import type { ComponentTemplateDTO } from "~/features/component-catalogue/model/ComponentTemplateDTO";
import type { WorkloadProfileDTO } from "~/features/component-catalogue/model/WorkloadProfileDTO";
import { cn } from "cn";
import { profileOf } from "../connections";
import { loadView, toneText } from "../execution-status";
import type { ExecutionDTO } from "../model/ExecutionDTO";
import type { CanvasAction, ComponentNode } from "../graph-reducer";
import type { ValidationIssueDTO } from "../model/ValidationIssueDTO";
import type { WorkloadDTO } from "../model/WorkloadDTO";
import IssueNote from "./issue-note";
import ParameterField from "./parameter-field";
import { StatusIcon } from "./status-icon";

// A summary value, or "—" when k6 did not report it: missing is never shown as 0.
function measure(value: number | null | undefined, unit = "") {
  if (value === null || value === undefined) return "—";
  return `${Number.isInteger(value) ? value : value.toFixed(1)}${unit}`;
}

// What the load did in the latest experiment: its state and, once it ended, k6's summary.
function LoadInExperiment({
  execution,
}: Readonly<{ execution: ExecutionDTO }>) {
  const view = loadView(execution);
  const load = execution.workload;
  if (!view || !load) return null;
  const summary = load.summary;
  const rows: [string, string][] = [
    ["requests", measure(summary?.requests)],
    ["failed", measure(summary?.failedRequests)],
    ["dropped", measure(summary?.droppedIterations)],
    ["average", measure(summary?.averageLatencyMs, " ms")],
    ["p95", measure(summary?.p95LatencyMs, " ms")],
  ];

  return (
    <section aria-labelledby="load-run-heading" className="flex flex-col gap-2">
      <h3 id="load-run-heading" className="text-sm font-medium">
        {execution.phase === "RELEASED"
          ? "Last experiment"
          : "In the experiment"}
      </h3>
      <p
        className={cn(
          "flex items-center gap-1.5 text-sm font-[520]",
          toneText[view.tone],
        )}
      >
        <StatusIcon tone={view.tone} />
        {view.label}
      </p>
      {load.summaryCollectedAt !== null ? (
        <dl
          aria-label="Load summary"
          className="flex flex-col gap-1 rounded-lg bg-background px-2.5 py-2 font-mono text-xs leading-4"
        >
          {rows.map(([name, value]) => (
            <div key={name} className="flex justify-between gap-3">
              <dt className="text-muted-foreground">{name}</dt>
              <dd className="tabular-nums">{value}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <p className="type-small text-muted-foreground">
          {execution.phase === "RELEASED"
            ? "k6 did not report a summary, for example because it never started."
            : "k6 reports its summary when the load ends or Stop halts it."}
        </p>
      )}
      {load.completed === false && (
        <p className="type-small text-muted-foreground">
          Stop or a failure cut the load short, so the summary covers only the
          time it ran.
        </p>
      )}
    </section>
  );
}

// A random seed in the range every load profile accepts.
export function randomSeed(max: number) {
  const [value] = crypto.getRandomValues(new Uint32Array(1));
  return value % (max + 1);
}

// The scenario load in the side panel (ADR 0012): the component that receives the traffic, the
// profile and its numbers. It is a setting of the scenario, not a node, so it has its own panel.
export default function WorkloadInspector({
  workload,
  profiles,
  nodes,
  templatesByKey,
  issues,
  execution,
  readOnly,
  dispatch,
  onClose,
}: Readonly<{
  workload: WorkloadDTO | null;
  profiles: WorkloadProfileDTO[];
  nodes: ComponentNode[];
  templatesByKey: Map<string, ComponentTemplateDTO>;
  // The issues on the load from the last save.
  issues: ValidationIssueDTO[];
  // The scenario's latest execution, to show what its load did.
  execution: ExecutionDTO | null;
  // An experiment holds the scenario: the form shows the values but changes nothing.
  readOnly: boolean;
  dispatch: Dispatch<CanvasAction>;
  onClose: () => void;
}>) {
  const targetId = useId();
  const profileId = useId();
  // A new seed replaces the seed field, which otherwise keeps what the user typed.
  const [seedVersion, setSeedVersion] = useState(0);
  const profile = profiles.find(
    (candidate) => candidate.name === workload?.profile,
  );
  const fieldIssue = (field: string) =>
    issues.find((issue) => issue.field === field);
  const shownFields = new Set([
    "targetKey",
    "profile",
    "seed",
    ...(profile?.parameters.map((parameter) => parameter.name) ?? []),
  ]);
  const otherIssues = issues.filter(
    (issue) => !issue.field || !shownFields.has(issue.field),
  );
  // A target must offer every capability the profile needs, such as HTTP.
  const accepts = (node: ComponentNode) => {
    const offered = profileOf(node, templatesByKey)?.capabilities ?? [];
    return (profile?.requires ?? []).every((capability) =>
      offered.includes(capability),
    );
  };
  // Only the components that can receive the load. A saved target that no longer fits (its profile
  // changed) stays listed so the field still names it, next to its error.
  const targets = nodes.filter(
    (node) => accepts(node) || node.id === workload?.targetKey,
  );
  const needs = profile?.requires.join(" and ") ?? "the load";
  const targetIssue = fieldIssue("targetKey");
  const profileIssue = fieldIssue("profile");

  return (
    <section aria-labelledby="load-heading" className="flex flex-col gap-5">
      <div className="flex min-w-0 items-center gap-3">
        <span
          aria-hidden
          className="grid size-8 shrink-0 place-items-center rounded-lg bg-accent"
        >
          <GaugeIcon className="size-4" strokeWidth={1.6} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="type-caption text-muted-foreground">Scenario</p>
          <h2
            id="load-heading"
            className="font-display text-[15px] leading-5 font-semibold"
          >
            Load
          </h2>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="Close load"
          onClick={onClose}
          className="shrink-0 self-start text-muted-foreground"
        >
          <XIcon />
        </Button>
      </div>

      {execution && <LoadInExperiment execution={execution} />}

      {workload === null ? (
        <div className="flex flex-col gap-3">
          <p className="type-small text-muted-foreground">
            The scenario has no load, so the experiment receives no traffic. A
            load sends requests to one component once every component is
            healthy.
          </p>
          <Button
            type="button"
            variant="outline"
            disabled={readOnly || profiles.length === 0}
            onClick={() =>
              dispatch({
                type: "workloadAdded",
                profile: profiles[0],
                seed: randomSeed(profiles[0].seed.max),
              })
            }
            className="self-start"
          >
            <GaugeIcon data-icon="inline-start" />
            Add load
          </Button>
        </div>
      ) : (
        // While an experiment runs, the disabled fieldset turns every field and button inside off.
        <fieldset disabled={readOnly} className="flex min-w-0 flex-col gap-5">
          <div className="flex flex-col gap-4">
            <Field>
              <FieldLabel htmlFor={targetId}>Target</FieldLabel>
              <Select
                value={workload.targetKey}
                items={targets.map((node) => ({
                  label: node.data.name,
                  value: node.id,
                }))}
                onValueChange={(value) =>
                  dispatch({
                    type: "workloadTargetChanged",
                    targetKey: value,
                  })
                }
              >
                <SelectTrigger id={targetId} className="w-full">
                  <SelectValue placeholder="Choose a component" />
                </SelectTrigger>
                <SelectContent alignItemWithTrigger={false} align="start">
                  {targets.map((node) => (
                    <SelectItem
                      key={node.id}
                      value={node.id}
                      disabled={!accepts(node)}
                    >
                      {node.data.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldDescription>
                {targets.length === 0
                  ? `Add a component that accepts ${needs} to receive the traffic.`
                  : `Only components that accept ${needs} are listed.`}
              </FieldDescription>
              {targetIssue && (
                <IssueNote
                  severity={targetIssue.severity}
                  message={targetIssue.message}
                />
              )}
            </Field>

            <Field>
              <FieldLabel htmlFor={profileId}>Profile</FieldLabel>
              <Select
                value={workload.profile}
                onValueChange={(value) => {
                  const next = profiles.find(
                    (candidate) => candidate.name === value,
                  );
                  if (next)
                    dispatch({ type: "workloadProfileChanged", profile: next });
                }}
              >
                <SelectTrigger id={profileId} className="w-full font-mono">
                  <SelectValue placeholder="Choose a profile" />
                </SelectTrigger>
                <SelectContent alignItemWithTrigger={false} align="start">
                  {profiles.map((candidate) => (
                    <SelectItem
                      key={candidate.name}
                      value={candidate.name}
                      className="font-mono"
                    >
                      {candidate.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {profileIssue && (
                <IssueNote
                  severity={profileIssue.severity}
                  message={profileIssue.message}
                />
              )}
            </Field>

            {profile?.parameters.map((parameter) => (
              <ParameterField
                key={`${workload.profile}-${parameter.name}`}
                parameter={parameter}
                value={workload.parameters[parameter.name]}
                issue={fieldIssue(parameter.name)}
                onChange={(value) =>
                  dispatch({
                    type: "workloadParameterChanged",
                    name: parameter.name,
                    value,
                  })
                }
              />
            ))}

            {profile && (
              <div className="flex flex-col gap-2">
                <ParameterField
                  key={seedVersion}
                  parameter={profile.seed}
                  value={workload.seed}
                  issue={fieldIssue("seed")}
                  // The seed always has a value; an empty field keeps the last one until it settles.
                  onChange={(value) => {
                    if (value !== undefined)
                      dispatch({ type: "workloadSeedChanged", seed: value });
                  }}
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    dispatch({
                      type: "workloadSeedChanged",
                      seed: randomSeed(profile.seed.max),
                    });
                    setSeedVersion((version) => version + 1);
                  }}
                  className="self-start text-muted-foreground"
                >
                  <DicesIcon data-icon="inline-start" />
                  New random seed
                </Button>
              </div>
            )}
          </div>

          {otherIssues.length > 0 && (
            <div className="flex flex-col gap-2">
              {otherIssues.map((issue) => (
                <IssueNote
                  key={`${issue.code}-${issue.field}`}
                  severity={issue.severity}
                  message={issue.message}
                />
              ))}
            </div>
          )}

          <Button
            type="button"
            variant="outline"
            onClick={() => dispatch({ type: "workloadRemoved" })}
            className="self-start"
          >
            <Trash2Icon data-icon="inline-start" />
            Remove load
          </Button>
        </fieldset>
      )}
    </section>
  );
}
