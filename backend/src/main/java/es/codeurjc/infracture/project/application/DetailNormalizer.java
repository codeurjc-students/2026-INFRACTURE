package es.codeurjc.infracture.project.application;

import java.text.Normalizer;

final class DetailNormalizer {

    private DetailNormalizer() {
    }

    static String normalizeText(String value) {
        return value == null ? null : Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    }

    static String normalizeDescription(String value) {
        String description = normalizeText(value);
        return description == null || description.isEmpty() ? null : description;
    }
}
