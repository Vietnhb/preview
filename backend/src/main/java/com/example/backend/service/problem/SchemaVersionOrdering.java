package com.example.backend.service.problem;

import com.example.backend.entity.problem.SchemaVersion;

import java.math.BigInteger;
import java.time.Instant;

/** Stable ordering for approved versions when publication timestamps tie. */
final class SchemaVersionOrdering {
    private SchemaVersionOrdering() { }

    static SchemaVersion newer(SchemaVersion left, SchemaVersion right) {
        int versionOrder = compareVersions(left.getVersion(), right.getVersion());
        if (versionOrder != 0) return versionOrder > 0 ? left : right;
        Instant leftCreated = left.getCreatedAt();
        Instant rightCreated = right.getCreatedAt();
        if (leftCreated != null && rightCreated != null) {
            int publicationOrder = leftCreated.compareTo(rightCreated);
            if (publicationOrder != 0) return publicationOrder > 0 ? left : right;
        } else if (leftCreated != null || rightCreated != null) {
            return leftCreated != null ? left : right;
        }
        return left;
    }

    static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[._-]", -1);
        String[] rightParts = right.split("[._-]", -1);
        for (int index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
            String leftPart = index < leftParts.length ? leftParts[index] : "0";
            String rightPart = index < rightParts.length ? rightParts[index] : "0";
            int order = comparePart(leftPart, rightPart);
            if (order != 0) return order;
        }
        return 0;
    }

    private static int comparePart(String left, String right) {
        try {
            return new BigInteger(left).compareTo(new BigInteger(right));
        } catch (NumberFormatException ignored) {
            return left.compareToIgnoreCase(right);
        }
    }
}
