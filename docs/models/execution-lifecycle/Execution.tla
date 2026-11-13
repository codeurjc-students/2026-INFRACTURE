---- MODULE Execution ----
\* Lifecycle of an Infracture Execution, as written in EXECUTION_ARCHITECTURE.md §9.5-§10,
\* ADR 0004, ADR 0005 and the #86 design. Two scenarios share one Docker host.
EXTENDS Naturals, FiniteSets

CONSTANTS Scen, FirstCauseWins

VARIABLES phase, desired, cause, resources, released, revision, snapRev, trouble, backendUp
vars == <<phase, desired, cause, resources, released, revision, snapRev, trouble, backendUp>>

Init ==
    /\ phase = [s \in Scen |-> "none"]
    /\ desired = [s \in Scen |-> "STOPPED"]
    /\ cause = [s \in Scen |-> "none"]
    /\ resources = [s \in Scen |-> "none"]
    /\ released = [s \in Scen |-> TRUE]
    /\ revision = [s \in Scen |-> 0]
    /\ snapRev = [s \in Scen |-> 0]
    /\ trouble = [s \in Scen |-> FALSE]
    /\ backendUp = TRUE

Rank(c) == IF c = "none" THEN 0 ELSE IF c \in {"stop", "limit"} THEN 1 ELSE IF c = "failure" THEN 2 ELSE 3

\* Every way to end asks for the same desired phase; the cause decides the final result.
NewCause(s, c) ==
    IF (FirstCauseWins /\ cause[s] # "none") \/ (~FirstCauseWins /\ Rank(c) <= Rank(cause[s]))
    THEN cause[s]
    ELSE c

Final(s) == IF cause[s] \in {"stop", "limit"} THEN "COMPLETED" ELSE "FAILED"

\* Run: save, validate and reserve the same revision; only one unreleased Execution globally.
UserRun(s) ==
    /\ backendUp
    /\ \A t \in Scen : released[t]
    /\ phase' = [phase EXCEPT ![s] = "RESERVED"]
    /\ desired' = [desired EXCEPT ![s] = "RUNNING"]
    /\ cause' = [cause EXCEPT ![s] = "none"]
    /\ released' = [released EXCEPT ![s] = FALSE]
    /\ snapRev' = [snapRev EXCEPT ![s] = revision[s]]
    /\ trouble' = [trouble EXCEPT ![s] = FALSE]
    /\ UNCHANGED <<resources, revision, backendUp>>

\* The scenario is editable only while it has no unreleased Execution.
UserSavesGraph(s) ==
    /\ backendUp /\ released[s]
    /\ revision' = [revision EXCEPT ![s] = 1 - revision[s]]
    /\ UNCHANGED <<phase, desired, cause, resources, released, snapRev, trouble, backendUp>>

PlanCompiled(s) ==
    /\ backendUp /\ phase[s] = "RESERVED" /\ desired[s] = "RUNNING"
    /\ phase' = [phase EXCEPT ![s] = "STARTING"]
    /\ UNCHANGED <<desired, cause, resources, released, revision, snapRev, trouble, backendUp>>

UpSucceeds(s) ==
    /\ backendUp /\ phase[s] = "STARTING"
    /\ phase' = [phase EXCEPT ![s] = "RUNNING"]
    /\ resources' = [resources EXCEPT ![s] = "some"]
    /\ UNCHANGED <<desired, cause, released, revision, snapRev, trouble, backendUp>>

\* Pull, start or health fails: record it and clean what was created.
UpFails(s) ==
    /\ backendUp /\ phase[s] = "STARTING"
    /\ phase' = [phase EXCEPT ![s] = "STOPPING"]
    /\ desired' = [desired EXCEPT ![s] = "STOPPED"]
    /\ cause' = [cause EXCEPT ![s] = NewCause(s, "failure")]
    /\ resources' = [resources EXCEPT ![s] = "some"]
    /\ trouble' = [trouble EXCEPT ![s] = TRUE]
    /\ UNCHANGED <<released, revision, snapRev, backendUp>>

\* Stop only sets the desired phase; during STARTING the engine then cancels the start (#88).
UserStop(s) ==
    /\ backendUp /\ phase[s] \in {"RESERVED", "STARTING", "RUNNING"} /\ desired[s] = "RUNNING"
    /\ desired' = [desired EXCEPT ![s] = "STOPPED"]
    /\ cause' = [cause EXCEPT ![s] = NewCause(s, "stop")]
    /\ UNCHANGED <<phase, resources, released, revision, snapRev, trouble, backendUp>>

\* The server reaches the deadline fixed when every node became healthy (#89). Like Stop, it only
\* asks for STOPPED, with a cause of the same rank, so the cleanup is the same.
TimeLimitReached(s) ==
    /\ backendUp /\ phase[s] = "RUNNING"
    /\ desired' = [desired EXCEPT ![s] = "STOPPED"]
    /\ cause' = [cause EXCEPT ![s] = NewCause(s, "limit")]
    /\ UNCHANGED <<phase, resources, released, revision, snapRev, trouble, backendUp>>

\* The engine sees the Stop while it waits for Docker and ends the start. It is not a failure: the
\* cause stays as it was, and the cleanup removes whatever the start created.
StartCancelled(s) ==
    /\ backendUp /\ phase[s] = "STARTING" /\ desired[s] = "STOPPED"
    /\ phase' = [phase EXCEPT ![s] = "STOPPING"]
    /\ resources' = [resources EXCEPT ![s] = "some"]
    /\ UNCHANGED <<desired, cause, released, revision, snapRev, trouble, backendUp>>

CleanupBegins(s) ==
    /\ backendUp /\ desired[s] = "STOPPED" /\ phase[s] \in {"RESERVED", "RUNNING"}
    /\ phase' = [phase EXCEPT ![s] = "STOPPING"]
    /\ UNCHANGED <<desired, cause, resources, released, revision, snapRev, trouble, backendUp>>

\* down -v, then check by label that nothing is left, then release.
CleanupSucceeds(s) ==
    /\ backendUp /\ phase[s] = "STOPPING"
    /\ phase' = [phase EXCEPT ![s] = Final(s)]
    /\ resources' = [resources EXCEPT ![s] = "none"]
    /\ released' = [released EXCEPT ![s] = TRUE]
    /\ UNCHANGED <<desired, cause, revision, snapRev, trouble, backendUp>>

\* The backend dies; Docker keeps whatever it had started.
BackendCrashes ==
    /\ backendUp
    /\ backendUp' = FALSE
    /\ phase' = [s \in Scen |-> IF released[s] THEN phase[s] ELSE "INTERRUPTED"]
    /\ resources' = [s \in Scen |-> IF phase[s] = "STARTING" THEN "some" ELSE resources[s]]
    /\ UNCHANGED <<desired, cause, released, revision, snapRev, trouble>>

\* On restart: record the interruption and clean up, never resume (ADR 0005).
BackendRestarts ==
    /\ ~backendUp
    /\ backendUp' = TRUE
    /\ phase' = [s \in Scen |-> IF released[s] THEN phase[s] ELSE "STOPPING"]
    /\ desired' = [s \in Scen |-> IF released[s] THEN desired[s] ELSE "STOPPED"]
    /\ cause' = [s \in Scen |-> IF released[s] THEN cause[s] ELSE NewCause(s, "crash")]
    /\ trouble' = [s \in Scen |-> IF released[s] THEN trouble[s] ELSE TRUE]
    /\ UNCHANGED <<resources, released, revision, snapRev>>

Next ==
    \/ \E s \in Scen :
        \/ UserRun(s) \/ UserSavesGraph(s) \/ PlanCompiled(s) \/ UpSucceeds(s) \/ UpFails(s)
        \/ UserStop(s) \/ TimeLimitReached(s) \/ StartCancelled(s) \/ CleanupBegins(s) \/ CleanupSucceeds(s)
    \/ BackendCrashes \/ BackendRestarts

Spec == Init /\ [][Next]_vars

\* Rules
OneActiveExecution == Cardinality({s \in Scen : ~released[s]}) <= 1
ReleasedLeavesNothing == \A s \in Scen : released[s] => resources[s] = "none"
RunsTheSavedRevision == \A s \in Scen : ~released[s] => revision[s] = snapRev[s]
TroubleIsNeverSuccess == \A s \in Scen : phase[s] = "COMPLETED" => ~trouble[s]
====
