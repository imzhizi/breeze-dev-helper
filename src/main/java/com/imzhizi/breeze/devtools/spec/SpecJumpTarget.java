package com.imzhizi.breeze.devtools.spec;

import java.util.Objects;

/**
 * Represents a parsed @spec annotation target
 */
public final class SpecJumpTarget {
    private final String relativePath;

    public SpecJumpTarget(String relativePath) {
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
    }

    public String getRelativePath() {
        return relativePath;
    }

    /**
     * Normalize path for matching: remove leading/trailing whitespace, convert backslashes
     */
    public String getNormalizedPath() {
        String normalized = relativePath.trim().replace("\\", "/");
        // Remove leading slash if present
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    @Override
    public String toString() {
        return "@spec " + relativePath;
    }
}
