/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks.compile

import org.gradle.api.internal.tasks.compile.processing.IncrementalAnnotationProcessorType
import org.gradle.language.fixtures.AnnotationProcessorFixture

class MultipleOriginIncrementalAnnotationProcessingIntegrationTest extends AbstractIncrementalAnnotationProcessingIntegrationTest {

    @Override
    def setup() {
        withProcessor(new AnnotationProcessorFixture().with {
            declaredType = IncrementalAnnotationProcessorType.MULTIPLE_ORIGIN
            actualType = IncrementalAnnotationProcessorType.MULTIPLE_ORIGIN
            it
        })
    }

    def "all sources are recompiled when any class changes"() {
        def a = java "@Helper class A {}"
        java "class B {}"

        outputs.snapshot { run "compileJava" }

        when:
        a.text = "@Helper class A { public void foo() {} }"
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "AggregatedHelper", "B")
    }

    def "the user is informed about non-incremental processors"() {
        def a = java "@Helper class A {}"

        when:
        run "compileJava"
        a.text = "@Helper class A { public void foo() {} }"
        run "compileJava", "--info"

        then:
        output.contains("The following annotation processors don't support incremental compilation:")
        output.contains("Processor (type: MULTIPLE_ORIGIN)")
    }

    def "processors must provide an originating element for each source element"() {
        given:
        withProcessor(new AnnotationProcessorFixture().with {
            annotationName = "Broken"
            declaredType = IncrementalAnnotationProcessorType.MULTIPLE_ORIGIN
            actualType = IncrementalAnnotationProcessorType.UNKNOWN
            it
        })
        java "@Broken class A {}"

        expect:
        fails"compileJava"

        and:
        errorOutput.contains("Generated file 'ABroken' must have at least one originating element.")
    }

    def "a single origin processor is also a valid multi origin processor"() {
        given:
        withProcessor(new AnnotationProcessorFixture().with {
            annotationName = "Single"
            declaredType = IncrementalAnnotationProcessorType.MULTIPLE_ORIGIN
            actualType = IncrementalAnnotationProcessorType.SINGLE_ORIGIN
            it
        })
        java "@Single class A {}"

        expect:
        succeeds"compileJava"
    }

    def "processors can provide multiple originating elements"() {
        given:
        java "@Helper class A {}"
        java "@Helper class B {}"

        expect:
        succeeds"compileJava"
    }
}
