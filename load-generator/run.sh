#!/bin/sh
# Runs the fixed script of the load profile. The plan only chooses the profile and passes numbers.
# exec hands k6 the container's signals, so Stop reaches k6 and it still prints its summary.
case "${WORKLOAD_PROFILE}" in
  CONSTANT_HTTP) script=/scripts/constant-http.js ;;
  *) echo "Unknown WORKLOAD_PROFILE: ${WORKLOAD_PROFILE}" >&2; exit 2 ;;
esac
exec k6 run --quiet "${script}"
