# The load image (ADR 0012): Grafana k6 plus one fixed script per load profile.
# WORKLOAD_PROFILE chooses the script; the other variables are only numbers and the target URL.
FROM grafana/k6:2.3.0
COPY scripts /scripts
COPY --chmod=755 run.sh /run.sh
ENTRYPOINT ["/run.sh"]
