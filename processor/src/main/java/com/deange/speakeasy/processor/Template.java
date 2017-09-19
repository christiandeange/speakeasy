package com.deange.speakeasy.processor;

import java.util.ArrayList;
import java.util.List;

import static com.deange.speakeasy.processor.StringUtils.isJavaIdentifier;

public class Template {

    private final String mName;
    private final String mValue;
    private final List<FieldConfig> mFields = new ArrayList<>();
    private final List<String> mFieldNames = new ArrayList<>();
    private final List<Part> mParts = new ArrayList<>();

    public Template(final String name, final String value) {
        mName = name;
        mValue = value;
    }

    public static Template parse(final String resName, final String resValue) {
        final Template template = new Template(resName, resValue);
        template.gatherFields();
        return template;
    }

    public List<FieldConfig> getFields() {
        return mFields;
    }

    public List<Part> getParts() {
        return mParts;
    }

    public String getName() {
        return mName;
    }

    public String getValue() {
        return mValue;
    }

    public boolean isValidTemplate() {
        return !mFields.isEmpty();
    }

    @Override
    public String toString() {
        return "Template {name='" + mName + "'}";
    }

    private void gatherFields() {
        int fieldStart = 0;
        int fieldEnd = -1;
        int anonymousFieldCount = 0;
        boolean isInCurlyBrace = false;

        for (int i = 0; i < mValue.length(); ++i) {
            final char c = mValue.charAt(i);

            if (c == '{') {
                if (isInCurlyBrace) {
                    throw new RuntimeException("Nested field template detected at position " + i);
                }

                isInCurlyBrace = true;
                fieldStart = i;

                final String literalSubstring = mValue.substring(fieldEnd + 1, fieldStart);
                if (!literalSubstring.isEmpty()) {
                    mParts.add(Part.literal(literalSubstring));
                }

            } else if (c == '}') {
                if (!isInCurlyBrace) {
                    throw new RuntimeException("Field unexpectedly ended at position " + i);
                }

                isInCurlyBrace = false;
                fieldEnd = i;

                String config = mValue.substring(fieldStart + 1, fieldEnd);
                if (config.isEmpty()) {
                    config = "arg" + anonymousFieldCount++;
                }

                final FieldConfig fieldConfig = FieldConfig.create(config);
                final String identifier = fieldConfig.getIdentifier();

                if (!isJavaIdentifier(identifier)) {
                    throw new RuntimeException(
                            "Field '" + identifier + "' is not a valid Java identifier");
                }

                if (mFieldNames.contains(identifier)) {
                    throw new RuntimeException("Duplicate field name: '" + identifier + "'");
                }

                mFieldNames.add(identifier);
                mFields.add(fieldConfig);
                mParts.add(Part.field(identifier));
            }
        }

        if (isInCurlyBrace) {
            throw new RuntimeException("Unterminated field starting at position " + fieldStart);
        }

        if (fieldEnd != mValue.length() - 1) {
            final String literalSubstring = mValue.substring(fieldEnd + 1);
            if (!literalSubstring.isEmpty()) {
                mParts.add(Part.literal(literalSubstring));
            }
        }
    }
}
