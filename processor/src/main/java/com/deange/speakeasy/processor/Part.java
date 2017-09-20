package com.deange.speakeasy.processor;

public abstract class Part {

    private final String mValue;

    protected Part(final String value) {
        mValue = value;
    }

    public static Part literal(final String literal) {
        return new Literal(literal);
    }

    public static Part field(final String fieldName) {
        return new Field(fieldName);
    }

    public String getValue() {
        return mValue;
    }

    public static class Literal extends Part {
        protected Literal(final String value) {
            super(value);
        }
    }

    public static class Field extends Part {
        protected Field(final String value) {
            super(value);
        }
    }

}
