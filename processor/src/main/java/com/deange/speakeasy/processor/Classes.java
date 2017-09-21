package com.deange.speakeasy.processor;

import com.squareup.javapoet.ClassName;

import static com.squareup.javapoet.ClassName.get;

public interface Classes {
    ClassName CONTEXT = get("android.content", "Context");
    ClassName PHRASE = get("com.squareup.phrase", "Phrase");
}
