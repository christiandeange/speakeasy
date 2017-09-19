package com.deange.speakeasy.processor;

import java.util.Arrays;
import java.util.List;

public final class StringUtils {

    private static final List<String> JAVA_KEYWORDS = Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "false",
            "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
            "int", "interface", "long", "native", "new", "null", "package", "private", "protected",
            "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "true", "try", "void", "volatile", "while"
    );

    private StringUtils() {
        throw new AssertionError();
    }

    public static boolean isEmpty(final String string) {
        return string == null || string.isEmpty();
    }

    public static boolean isJavaIdentifier(final String identifier) {
        if (identifier.isEmpty()) {
            return false;
        } else if (JAVA_KEYWORDS.contains(identifier)) {
            return false;
        } else if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
            return false;
        } else {
            for (int i = 1; i < identifier.length(); ++i) {
                if (!Character.isJavaIdentifierPart(identifier.charAt(i))) {
                    return false;
                }
            }

            return true;
        }
    }

    public static String snakeCaseToCamelCase(final String snakeCase) {
        return snakeCaseToCamelCase(snakeCase, false);
    }

    public static String snakeCaseToCamelCase(
            final String snakeCase,
            final boolean capitalizeFirst) {
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
