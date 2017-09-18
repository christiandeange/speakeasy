package com.deange.speakeasy.processor;

import java.util.ArrayList;
import java.util.List;

public class Template {

    public final String name;
    public final String value;

    private String mAlias;

    private final List<String> mFields = new ArrayList<>();
    private final List<Part> mParts = new ArrayList<>();

    public Template(final String name, final String value) {
        this.name = name;
        this.value = value;
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
            return StringUtils.snakeCaseToCamelCase(name);
        }
    }

    public void setAlias(final String alias) {
        mAlias = alias;
    }

    public boolean isValidTemplate() {
        return !mFields.isEmpty();
    }

    @Override
    public String toString() {
        return "Template {name='" + name + "'}";
    }

    private void gatherFields() {
        int fieldStart = 0;
        int fieldEnd = -1;
        int anonymousFieldCount = 0;
        boolean isInCurlyBrace = false;
        boolean verifyJavaStart = false;

        for (int i = 0; i < value.length(); ++i) {
            final char c = value.charAt(i);

            if (c == '{') {
                if (isInCurlyBrace) {
                    throw new RuntimeException("Nested field template detected at position " + i);
                }

                isInCurlyBrace = true;
                verifyJavaStart = true;
                fieldStart = i;

                final String literalSubstring = value.substring(fieldEnd + 1, fieldStart);
                if (literalSubstring.length() != 0) {
                    mParts.add(Part.literal(literalSubstring));
                }

            } else if (c == '}') {
                if (!isInCurlyBrace) {
                    throw new RuntimeException("Field unexpectedly ended at position " + i);
                }

                isInCurlyBrace = false;
                fieldEnd = i;

                String fieldName = value.substring(fieldStart + 1, fieldEnd);
                if (fieldName.length() == 0) {
                    fieldName = "arg" + anonymousFieldCount++;
                }

                if (mFields.contains(fieldName)) {
                    throw new RuntimeException("Duplicate field name '" + fieldName + "'");
                }

                mFields.add(fieldName);
                mParts.add(Part.field(fieldName));

            } else {
                if (isInCurlyBrace) {
                    if (verifyJavaStart && !Character.isJavaIdentifierStart(c)) {
                        throw new RuntimeException("Illegal java identifier starting character '" + c + "'");
                    }
                    if (!verifyJavaStart && !Character.isJavaIdentifierPart(c)) {
                        throw new RuntimeException("Illegal java identifier character '" + c + "'");
                    }
                    verifyJavaStart = false;
                }
            }
        }

        if (isInCurlyBrace) {
            throw new RuntimeException("Unterminated field starting at position " + fieldStart);
        }

        if (fieldEnd != value.length() - 1) {
            final String literalSubstring = value.substring(fieldEnd + 1);
            if (literalSubstring.length() != 0) {
                mParts.add(Part.literal(literalSubstring));
            }
        }
    }
}
