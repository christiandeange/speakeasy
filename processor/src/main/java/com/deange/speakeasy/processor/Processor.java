package com.deange.speakeasy.processor;

import com.deange.speakeasy.StringTemplates;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.xml.parsers.DocumentBuilderFactory;

import static com.deange.speakeasy.processor.Processor.OPTION_KEY;
import static com.deange.speakeasy.processor.StringUtils.isJavaIdentifier;

@SupportedAnnotationTypes("com.deange.speakeasy.StringTemplates")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedOptions(OPTION_KEY)
public class Processor
        extends AbstractProcessor
        implements
        Classes,
        Annotations {

    public static final String PACKAGE_NAME = "com.deange.speakeasy.generated";
    public static final String OPTION_KEY = "resDirs";

    private final Map<String, Template> mTemplates = new HashMap<>();
    private TypeMirror rDotJavaFile;

    @Override
    public boolean process(final Set<? extends TypeElement> set, final RoundEnvironment env) {
        if (set.size() == 0 || env.processingOver()) {
            return true;
        }

        mTemplates.clear();

        if (!processingEnv.getOptions().containsKey(OPTION_KEY)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "\"" + OPTION_KEY + "\" annotation processor argument not provided");
            return true;
        }

        final AnnotatedConstruct annotatedElement =
                env.getElementsAnnotatedWith(StringTemplates.class).iterator().next();
        try {
            // This will force a reflective exception
            annotatedElement.getAnnotation(StringTemplates.class).value();
        } catch (final MirroredTypeException mte) {
            rDotJavaFile = mte.getTypeMirror();
        }

        final String[] resDirs = processingEnv.getOptions().get(OPTION_KEY).split("\n");

        Arrays.stream(resDirs)
              .map(File::new)
              .filter(File::exists)
              .forEach(this::processFolder);

        generateTemplatesJavaFile();

        return true;
    }

    private void processFolder(final File resDir) {
        FileUtils.listFiles(resDir, new String[]{"xml"}, true).forEach(this::processFile);
    }

    private void processFile(final File xmlFile) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            final Document document = factory.newDocumentBuilder().parse(xmlFile);

            final NodeList nodes = document.getElementsByTagName("string");
            for (int i = 0; i < nodes.getLength(); ++i) {
                final Element node = (Element) nodes.item(i);
                processString(node);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void processString(final Element node) {
        final String resName = node.getAttribute("name");
        final String alias = node.getAttribute("alias");
        final String resValue = node.getTextContent();

        final String templateName = alias.isEmpty() ? resName : alias;

        if (!isJavaIdentifier(templateName)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Field '" + templateName + "' is not a valid Java identifier");
            return;
        }

        final Template template = Template.parse(templateName, resName, resValue);

        if (template.isValidTemplate()) {
            mTemplates.put(resName, template);
        }
    }

    private TypeSpec generateClassForTemplate(
            final Template template,
            final TypeSpec... interfaces) {

        final String resName = template.getResName();
        final String className = StringUtils.snakeCaseToCamelCase(resName, true);
        final ClassName clazz = ClassName.get(PACKAGE_NAME, className);

        final List<FieldConfig> fields = template.getFields();

        final TypeSpec.Builder templateClass = TypeSpec.classBuilder(className);
        templateClass.addModifiers(Modifier.PUBLIC);
        templateClass.addSuperinterfaces(
                Arrays.stream(interfaces)
                      .map(superInterface -> ClassName.get(PACKAGE_NAME, superInterface.name))
                      .collect(Collectors.toList()));

        // Phrase member field
        templateClass.addField(
                FieldSpec.builder(PHRASE, "mPhrase")
                         .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                         .build()
        );

        // Package-private constructor
        templateClass.addMethod(
                MethodSpec.constructorBuilder()
                          .addParameter(
                                  ParameterSpec.builder(CONTEXT, "context")
                                               .addAnnotation(NONNULL)
                                               .build())
                          .addStatement("mPhrase = $T.from(context, $T.string.$L)",
                                        PHRASE, rDotJavaFile, resName)
                          .build());

        for (final FieldConfig field : fields) {
            final String name = field.getIdentifier();
            final String format = field.getFormat();

            final MethodSpec.Builder fieldBuilder =
                    MethodSpec.methodBuilder(name)
                              .addModifiers(Modifier.PUBLIC)
                              .returns(clazz);

            if (format == null) {
                fieldBuilder.addParameter(
                        ParameterSpec.builder(CharSequence.class, name)
                                     .addAnnotation(NONNULL)
                                     .build())
                            .addStatement("mPhrase.put($S, $L)", name, name);
            } else {
                // Use varargs to match String.format(String, Object...) method signature
                fieldBuilder.addParameter(Object[].class, name).varargs()
                            .addStatement("mPhrase.put($S, String.format($S, $L))",
                                          name, format, name);
            }

            templateClass.addMethod(
                    fieldBuilder.addStatement("return this")
                                .build());
        }

        final MethodSpec.Builder build =
                MethodSpec.methodBuilder("build")
                          .addAnnotation(Annotations.OVERRIDE)
                          .addModifiers(Modifier.PUBLIC)
                          .returns(CharSequence.class)
                          .addStatement("return mPhrase.format()");

        templateClass.addMethod(build.build());

        return templateClass.build();
    }

    private void generateTemplatesJavaFile() {

        // Create Buildable interface used for each template class
        final MethodSpec build =
                MethodSpec.methodBuilder("build")
                          .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                          .returns(CharSequence.class)
                          .build();

        final TypeSpec buildable = TypeSpec.interfaceBuilder("Buildable").addMethod(build).build();
        writeToFile(buildable);

        // Generate and collect all of the template classes
        final List<MethodSpec> methods = new ArrayList<>();

        for (final Template template : mTemplates.values()) {
            final TypeSpec typeSpec = generateClassForTemplate(template, buildable);

            final ClassName templateClass = ClassName.get(PACKAGE_NAME, typeSpec.name);
            final MethodSpec method =
                    MethodSpec.methodBuilder(template.getTemplateName())
                              .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                              .returns(templateClass)
                              .addParameter(
                                      ParameterSpec.builder(CONTEXT, "context")
                                                   .addAnnotation(NONNULL)
                                                   .build())
                              .addStatement("return new $N(context)", typeSpec)
                              .build();
            methods.add(method);

            writeToFile(typeSpec);
        }

        // Create the main Templates class
        final TypeSpec templatesClass =
                TypeSpec.classBuilder("Templates")
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                        .addAnnotation(Annotations.SUPPRESS_WARNINGS_UNUSED)
                        .addMethods(methods)
                        .build();

        writeToFile(templatesClass);
    }

    private void writeToFile(final TypeSpec typeSpec) {
        final JavaFile javaFile = JavaFile.builder(PACKAGE_NAME, typeSpec)
                                          .skipJavaLangImports(true)
                                          .build();

        try {
            if (Boolean.parseBoolean(processingEnv.getOptions().get("debug"))) {
                javaFile.writeTo(System.out);
            }
            javaFile.writeTo(processingEnv.getFiler());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

}
