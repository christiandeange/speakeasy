package com.deange.speakeasy.processor;

import java.util.ArrayList;
import java.util.List;

import static com.deange.speakeasy.processor.StringUtils.isJavaIdentifier;

public class Template {

    private final String mTemplateName;
    private final String mResName;
    private final String mValue;

    private final List<FieldConfig> mFields = new ArrayList<>();
    private final List<String> mFieldNames = new ArrayList<>();
    private final List<Part> mParts = new ArrayList<>();

    private Template(final String templateName, final String resName, final String value) {
        mTemplateName = templateName;
        mResName = resName;
        mValue = value;
    }

    public static Template parse(
            final String templateName,
            final String resName,
            final String resValue) {
        final Template template = new Template(templateName, resName, resValue);
        template.parseFields();
        return template;
    }

    public List<FieldConfig> getFields() {
        return mFields;
    }

    public List<Part> getParts() {
        return mParts;
    }

    public String getTemplateName() {
        return mTemplateName;
    }

    public String getResName() {
        return mResName;
    }

    public String getValue() {
        return mValue;
    }

    public boolean isValidTemplate() {
        return !mFields.isEmpty();
    }

    @Override
    public String toString() {
        return "Template {name='" + mTemplateName + "'}";
    }

    private void parseFields() {
        int fieldStart = -1;
        int fieldEnd = -1;
        boolean parsingFieldName = false;

        for (int i = 0; i < mValue.length(); ++i) {
            final char c = mValue.charAt(i);

            // Allow escaping curly braces
            if (c == '\\') {
                i++;
                continue;
            }

            if (c == '{') {
                if (parsingFieldName) {
                    failParse("Nested field template detected at position " + i);
                }

                parsingFieldName = true;
                fieldStart = i;

                final String literalString = mValue.substring(fieldEnd + 1, fieldStart);
                parseLiteral(literalString);

            } else if (c == '}') {
                if (!parsingFieldName) {
                    failParse("Field unexpectedly ended at position " + i);
                }

                parsingFieldName = false;
                fieldEnd = i;

                final String configString = mValue.substring(fieldStart + 1, fieldEnd);
                parseField(configString);
            }
        }

        if (parsingFieldName) {
            failParse("Unterminated field starting at position " + fieldStart);
        }

        if (fieldEnd != mValue.length() - 1) {
            final String literalSubstring = mValue.substring(fieldEnd + 1);
            if (!literalSubstring.isEmpty()) {
                mParts.add(Part.literal(literalSubstring));
            }
        }
    }

    private void parseField(final String fieldString) {
        final FieldConfig fieldConfig = FieldConfig.create(fieldString);
        final String identifier = fieldConfig.getIdentifier();

        if (!isJavaIdentifier(identifier)) {
            failParse("Field '" + identifier + "' is not a valid Java identifier");
        }

        if (mFieldNames.contains(identifier)) {
            failParse("Duplicate field name: '" + identifier + "'");
        }

        mFieldNames.add(identifier);
        mFields.add(fieldConfig);
        mParts.add(Part.field(identifier));
    }

    private void parseLiteral(final String literalString) {
        if (!literalString.isEmpty()) {
            mParts.add(Part.literal(literalString));
        }
    }

    private void failParse(final String message) {
        throw new RuntimeException("Template '" + mTemplateName + "': " + message);
    }
}
