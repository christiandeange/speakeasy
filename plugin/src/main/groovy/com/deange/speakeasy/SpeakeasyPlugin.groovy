package com.deange.speakeasy

import org.gradle.api.Plugin
import org.gradle.api.Project

class SpeakeasyPlugin implements Plugin<Project> {

    void apply(final Project project) {
        project.afterEvaluate {
            project.android.applicationVariants.all { variant ->
                def taskName = "resDirs${variant.buildType.name.capitalize()}"
                def task = project.tasks.create(taskName).doLast {
                    def resDirs = variant.sourceSets.collect { it.resDirectories }.flatten()
                    javaCompile.options.compilerArgs += "-AresDirs=${resDirs.join('\n')}"
                }

                variant.preBuild.dependsOn task
            }
        }
    }
}
