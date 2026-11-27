package es.codeurjc.infracture.demo;

import java.util.OptionalDouble;

final class PrometheusText {

    private PrometheusText() {
    }

    static OptionalDouble counter(String body, String metric) {
        double sum = 0.0;
        boolean found = false;
        for (String raw : body.lines().toList()) {
            String line = raw.stripLeading();
            if (line.isEmpty() || line.startsWith("#") || !line.startsWith(metric)) {
                continue;
            }
            if (line.length() <= metric.length()) {
                continue;
            }
            char next = line.charAt(metric.length());
            int index;
            if (next == '{') {
                int close = line.indexOf('}', metric.length());
                if (close < 0) {
                    continue;
                }
                index = close + 1;
            } else if (next == ' ' || next == '\t') {
                index = metric.length();
            } else {
                continue;
            }
            String rest = line.substring(index).stripLeading();
            if (rest.isEmpty()) {
                continue;
            }
            String token = rest.split("\\s+", 2)[0];
            sum += Double.parseDouble(token);
            found = true;
        }
        return found ? OptionalDouble.of(sum) : OptionalDouble.empty();
    }
}
