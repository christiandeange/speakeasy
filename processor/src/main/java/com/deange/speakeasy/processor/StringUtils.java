package com.deange.speakeasy.processor;

public final class StringUtils {

    private StringUtils() {
        throw new AssertionError();
    }

    public static boolean isEmpty(final String string) {
        return string == null || string.isEmpty();
    }

    public static boolean isJavaIdentifier(final String identifier) {
        if (isEmpty(identifier)) {
            return false;
        }

        boolean verifyJavaStart = true;
        for (final char c : identifier.toCharArray()) {
            if (verifyJavaStart && !Character.isJavaIdentifierStart(c)) {
                return false;
            } else if (!verifyJavaStart && !Character.isJavaIdentifierPart(c)) {
                return false;
            }
            verifyJavaStart = false;
        }

        return true;
    }

    public static String snakeCaseToCamelCase(final String snakeCase) {
        return snakeCaseToCamelCase(snakeCase, false);
    }

    public static String snakeCaseToCamelCase(final String snakeCase, final boolean capitalizeFirst) {
        final String[] parts = snakeCase.split("_");

        final StringBuilder sb = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            final String part = parts[i];
            if (part.length() != 0) {
                if (i == 0 && !capitalizeFirst) {
                    sb.append(part);
                } else {
                    sb.append(part.substring(0, 1).toUpperCase());
                    sb.append(part.substring(1));
                }
            }
        }

        return sb.toString();
    }
}
