package com.deange.speakeasy.processor;

import java.util.ArrayList;
import java.util.List;

public class Template {

    private final String mName;
    private final String mValue;
    private final List<String> mFields = new ArrayList<>();
    private final List<Part> mParts = new ArrayList<>();

    private String mAlias;

    public Template(final String name, final String value) {
        this.mName = name;
        this.mValue = value;
        this.mAlias = name;
    }

    public static Template parse(final String resName, final String resValue) {
        final Template template = new Template(resName, resValue);
        template.gatherFields();
        return template;
    }

    public List<String> getFields() {
        return mFields;
    }

    public List<Part> getParts() {
        return mParts;
    }

    public String getMethodName() {
        if (!StringUtils.isEmpty(mAlias)) {
            return mAlias;
        } else {
            return StringUtils.snakeCaseToCamelCase(mName);
        }
    }

    public void setAlias(final String alias) {
        mAlias = alias;
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
                if (literalSubstring.length() != 0) {
                    mParts.add(Part.literal(literalSubstring));
                }

            } else if (c == '}') {
                if (!isInCurlyBrace) {
                    throw new RuntimeException("Field unexpectedly ended at position " + i);
                }

                isInCurlyBrace = false;
                fieldEnd = i;

                String fieldName = mValue.substring(fieldStart + 1, fieldEnd);
                if (fieldName.length() == 0) {
                    fieldName = "arg" + anonymousFieldCount++;
                }

                if (!StringUtils.isJavaIdentifier(fieldName)) {
                    throw new RuntimeException(
                            "Field '" + fieldName + "' is not a valid Java identifier");
                }

                if (mFields.contains(fieldName)) {
                    throw new RuntimeException("Duplicate field name: '" + fieldName + "'");
                }

                mFields.add(fieldName);
                mParts.add(Part.field(fieldName));
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
