/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.fixtures

import groovy.transform.CompileStatic
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector
import org.gradle.api.internal.tasks.compile.processing.IncrementalAnnotationProcessorType
import org.gradle.test.fixtures.file.TestFile

/**
 * Generates an annotation processor and a support library. The processor generates a class
 * that prints a simple message when its getValue method is called. The printed message is
 * computed with the help of the support library.
 *
 * <p>
 * The generated processor depends on the options set:
 *
 * <ul>
 * <li>The annotation name controls which annotation the processor reacts to. By default the annotation is <code>@Helper</code></li>
 * <li>The message is baked into the support library. A change of this field can be used to simulate a change to that library.</li>
 * <li>The suffix is baked into the processor itself. A change of this field can be used to simulate a change to the processor.</li>
 * <li>The declared type controls which incremental annotation processing mode the processor registers itself for.</li>
 * <li>The actual type controls what its actual behavior is, whether it provides 0, 1 or multiple source elements for the generated sources.</li>
 *   If the actual type is UNKNOWN or SINGLE_ORIGIN, it will generate one class for each source file. The generated class will be named
 *   "sourceClassName$annotationName". For the UNKNOWN type, no originating elements will be provided. For SINGLE_ORIGIN, the single originating
 *   element will be provided. If the actual type is MULTIPLE_ORIGIN then only one class will be generated, whose name is always "Aggregated$annotationName".
 *   It's originating elements will be the set of all annotated elements.</li>
 * </ul>
 * </p>
 */
@CompileStatic
class AnnotationProcessorFixture {
    String annotationName = "Helper"
    String message = "greetings"
    private String suffix = ""
    IncrementalAnnotationProcessorType declaredType
    IncrementalAnnotationProcessorType actualType

    void setSuffix(String suffix) {
        this.suffix = suffix ? " " + suffix : ""
    }

    def writeApiTo(TestFile projectDir) {
        // Annotation handled by processor
        projectDir.file("src/main/java/${annotationName}.java").text = """
            public @interface $annotationName {
            }
"""
    }

    def writeSupportLibraryTo(TestFile projectDir) {
        // Some library class used by processor at runtime
        def utilClass = projectDir.file("src/main/java/${annotationName}Util.java")
        utilClass.text = """
            class ${annotationName}Util {
                static String getValue() { return "${message}"; }
            }
"""
    }

    def writeAnnotationProcessorTo(TestFile projectDir) {
        // The annotation processor
        projectDir.file("src/main/java/${annotationName}Processor.java").text = """
            import javax.annotation.processing.AbstractProcessor;
            import java.util.Set;
            import java.util.Collections;
            import java.io.Writer;
            import javax.lang.model.SourceVersion;
            import javax.lang.model.util.Elements;
            import javax.annotation.processing.Filer;
            import javax.annotation.processing.Messager;
            import javax.lang.model.element.Element;
            import javax.lang.model.element.TypeElement;
            import javax.tools.JavaFileObject;
            import javax.annotation.processing.ProcessingEnvironment;
            import javax.annotation.processing.RoundEnvironment;
            import javax.annotation.processing.SupportedOptions;
            import javax.tools.Diagnostic;
                                       
            @SupportedOptions({ "message" })
            public class ${annotationName}Processor extends AbstractProcessor {
                private Elements elementUtils;
                private Filer filer;
                private Messager messager;
                private String messageFromOptions;
    
                @Override
                public Set<String> getSupportedAnnotationTypes() {
                    return Collections.singleton(${annotationName}.class.getName());
                }
            
                @Override
                public SourceVersion getSupportedSourceVersion() {
                    return SourceVersion.latestSupported();
                }
    
                @Override
                public synchronized void init(ProcessingEnvironment processingEnv) {
                    elementUtils = processingEnv.getElementUtils();
                    filer = processingEnv.getFiler();
                    messager = processingEnv.getMessager();
                    messageFromOptions = processingEnv.getOptions().get("message");
                }
    
                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                    for (TypeElement annotation : annotations) {
                        if (annotation.getQualifiedName().toString().equals(${annotationName}.class.getName())) {
                            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
                            ${generatorCode}
                        }
                    }
                    return true;
                }
            }
"""
        projectDir.file("src/main/resources/$AnnotationProcessorDetector.PROCESSOR_DECLARATION").text = "${annotationName}Processor"
        if (declaredType) {
            projectDir.file("src/main/resources/$AnnotationProcessorDetector.INCREMENTAL_PROCESSOR_DECLARATION").text = "${annotationName}Processor,$declaredType"
        }
    }

    String getGeneratorCode() {
        switch(actualType) {
            case IncrementalAnnotationProcessorType.SINGLE_ORIGIN:
                return singleOriginCode
            case IncrementalAnnotationProcessorType.MULTIPLE_ORIGIN:
                return multipleOriginCode
            default:
                return unknownCode
        }
    }

    String getSingleOriginCode() {
        """
for (Element element : elements) {
    TypeElement typeElement = (TypeElement) element;
    String className = typeElement.getSimpleName().toString() + "${annotationName}";
    try {
        JavaFileObject sourceFile = filer.createSourceFile(className, element);
        Writer writer = sourceFile.openWriter();
        try {
            writer.write("class " + className + " {");
            writer.write("    String getValue() { return \\"");
            if (messageFromOptions == null) {
                writer.write(${annotationName}Util.getValue() + "${suffix}");
            } else {
                writer.write(messageFromOptions);
            }
            writer.write("\\"; }");
            writer.write("}");
        } finally {
            writer.close();
        }
    } catch (Exception e) {
        messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate source file " + className, element);
    }
}
"""
    }

    String getMultipleOriginCode() {

        """
String className = "Aggregated${annotationName}";
try {
    JavaFileObject sourceFile = filer.createSourceFile(className, elements.toArray(new Element[0]));
    Writer writer = sourceFile.openWriter();
    try {
        writer.write("class " + className + " {");
        writer.write("    String getValue() { return \\"");
        if (messageFromOptions == null) {
            writer.write(${annotationName}Util.getValue() + "${suffix}");
        } else {
            writer.write(messageFromOptions);
        }
        writer.write("\\"; }");
        writer.write("}");
    } finally {
        writer.close();
    }
} catch (Exception e) {
    messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate source file " + className);
}
"""
    }

    String getUnknownCode() {

        """
for (Element element : elements) {
    TypeElement typeElement = (TypeElement) element;
    String className = typeElement.getSimpleName().toString() + "${annotationName}";
    try {
        JavaFileObject sourceFile = filer.createSourceFile(className);
        Writer writer = sourceFile.openWriter();
        try {
            writer.write("class " + className + " {");
            writer.write("    String getValue() { return \\"");
            if (messageFromOptions == null) {
                writer.write(${annotationName}Util.getValue() + "${suffix}");
            } else {
                writer.write(messageFromOptions);
            }
            writer.write("\\"; }");
            writer.write("}");
        } finally {
            writer.close();
        }
    } catch (Exception e) {
        messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate source file " + className, element);
    }
}
"""
    }
}
