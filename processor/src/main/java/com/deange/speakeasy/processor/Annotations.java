package com.deange.speakeasy.processor;

import com.squareup.javapoet.AnnotationSpec;

import static com.squareup.javapoet.ClassName.get;

public interface Annotations {

    AnnotationSpec NONNULL =
            AnnotationSpec.builder(get("android.support.annotation", "NonNull"))
                          .build();

    AnnotationSpec OVERRIDE =
            AnnotationSpec.builder(Override.class)
                          .build();

    AnnotationSpec SUPPRESS_WARNINGS_UNUSED =
            AnnotationSpec.builder(SuppressWarnings.class)
                          .addMember("value", "{$S}", "unused")
                          .build();
}
