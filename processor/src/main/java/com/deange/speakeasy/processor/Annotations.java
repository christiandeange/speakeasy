package com.deange.speakeasy.processor;

import com.squareup.javapoet.AnnotationSpec;

public interface Annotations {

    AnnotationSpec OVERRIDE =
            AnnotationSpec.builder(Override.class)
                          .build();

    AnnotationSpec SUPPRESS_WARNINGS_UNUSED =
            AnnotationSpec.builder(SuppressWarnings.class)
                          .addMember("value", "{$S}", "unused")
                          .build();
}
