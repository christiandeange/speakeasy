package com.deange.speakeasy.processor;

public class FieldConfig {

    private final String mIdentifier;
    private final String mFormat;

    public FieldConfig(final String identifier, final String format) {
        mIdentifier = identifier;
        mFormat = format;
    }

    public static FieldConfig create(final String fieldConfig) {
        final String identifier;
        final String format;

        if (fieldConfig.contains("|")) {
            final String[] parts = fieldConfig.split("\\|");

            format = parts[0];
            identifier = parts[1];
        } else {
            identifier = fieldConfig;
            format = null;
        }

        return new FieldConfig(identifier, format);
    }

    public String getIdentifier() {
        return mIdentifier;
    }

    public String getFormat() {
        return mFormat;
    }
}
