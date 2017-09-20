package com.deange.speakeasy.processor;

import com.squareup.javapoet.CodeBlock;

import java.util.Collections;

public final class CodeUtils {

    private CodeUtils() {
        throw new AssertionError();
    }

    public static void indent(final CodeBlock.Builder builder, final int indentLevels) {
        builder.add(String.join("", Collections.nCopies(indentLevels, "$>")));
    }

    public static void unindent(final CodeBlock.Builder builder, final int unindentLevels) {
        builder.add(String.join("", Collections.nCopies(unindentLevels, "$<")));
    }

}
