package com.deange.speakeasy.processor;

import com.squareup.javapoet.MethodSpec;

public abstract class Part {

    protected final String mValue;

    protected Part(final String value) {
        mValue = value;
    }

    public static Part literal(final String literal) {
        return new Literal(literal);
    }

    public static Part field(final String fieldName) {
        return new Field(fieldName);
    }

    public abstract void appendValue(final MethodSpec.Builder builder);

    private static class Literal extends Part {
        protected Literal(final String value) {
            super(value);
        }

        @Override
        public void appendValue(final MethodSpec.Builder builder) {
            builder.addCode(".append($S)", mValue);
        }
    }

    private static class Field extends Part {
        protected Field(final String value) {
            super(value);
        }

        @Override
        public void appendValue(final MethodSpec.Builder builder) {
            builder.addCode(".append($L)", mValue);
        }
    }

}
