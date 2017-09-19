package com.deange.speakeasy.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
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
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.xml.parsers.DocumentBuilderFactory;

import static com.deange.speakeasy.processor.Processor.OPTION_KEY;
import static com.deange.speakeasy.processor.StringUtils.isJavaIdentifier;

@SupportedAnnotationTypes("com.deange.speakeasy.StringTemplates")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedOptions(OPTION_KEY)
public class Processor extends AbstractProcessor {
    public static final String PACKAGE_NAME = "com.deange.speakeasy.generated";
    public static final String OPTION_KEY = "resDirs";

    private final Map<String, Template> mTemplates = new HashMap<>();

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

        final Template template = Template.parse(templateName, resValue);

        if (template.isValidTemplate()) {
            mTemplates.put(resName, template);
        }
    }

    private TypeSpec generateClassForTemplate(
            final String resName,
            final Template resTemplate,
            final TypeSpec... interfaces) {

        final String className = StringUtils.snakeCaseToCamelCase(resName, true);
        final ClassName clazz = ClassName.get(PACKAGE_NAME, className);

        final String original = resTemplate.getValue();
        final List<FieldConfig> fields = resTemplate.getFields();

        final TypeSpec.Builder template = TypeSpec.classBuilder(className);
        template.addModifiers(Modifier.PUBLIC);
        template.addSuperinterfaces(
                Arrays.stream(interfaces)
                      .map(superInterface -> ClassName.get(PACKAGE_NAME, superInterface.name))
                      .collect(Collectors.toList()));

        // Package-private constructor
        template.addMethod(MethodSpec.constructorBuilder().build());

        for (final FieldConfig field : fields) {
            final String name = field.getIdentifier();
            final String format = field.getFormat();

            template.addField(
                    FieldSpec.builder(String.class, name)
                             .addModifiers(Modifier.PRIVATE)
                             .build()
            );

            final MethodSpec.Builder fieldBuilder =
                    MethodSpec.methodBuilder(name)
                              .addModifiers(Modifier.PUBLIC)
                              .returns(clazz);

            if (format == null) {
                fieldBuilder.addParameter(String.class, name)
                            .addStatement("this.$N = $N", name, name);
            } else {
                // Use varargs to match String.format(String, Object...) method signature
                fieldBuilder.addParameter(Object[].class, name).varargs()
                            .addStatement("this.$N = String.format($S, $N)", name, format, name);
            }

            template.addMethod(
                    fieldBuilder.addStatement("return this")
                                .build());
        }

        final MethodSpec.Builder build =
                MethodSpec.methodBuilder("build")
                          .addAnnotation(Annotations.OVERRIDE)
                          .addModifiers(Modifier.PUBLIC)
                          .returns(String.class);

        if (fields.isEmpty()) {
            // No fields in the string value
            build.addStatement("return $S", original);

        } else {
            build.addCode("return new $T()", StringBuilder.class);
            resTemplate.getParts().forEach(part -> part.appendValue(build));
            build.addCode(".toString();\n");
        }

        template.addMethod(build.build());

        return template.build();
    }

    private void generateTemplatesJavaFile() {

        // Create Buildable interface used for each template class
        final MethodSpec build =
                MethodSpec.methodBuilder("build")
                          .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                          .returns(String.class)
                          .build();

        final TypeSpec buildable = TypeSpec.interfaceBuilder("Buildable").addMethod(build).build();
        writeToFile(buildable);

        // Generate and collect all of the template classes
        final List<MethodSpec> methods = new ArrayList<>();

        for (final Map.Entry<String, Template> entry : mTemplates.entrySet()) {
            final String resName = entry.getKey();
            final Template template = entry.getValue();

            final TypeSpec typeSpec = generateClassForTemplate(resName, template, buildable);

            final ClassName templateClass = ClassName.get(PACKAGE_NAME, typeSpec.name);
            final MethodSpec method =
                    MethodSpec.methodBuilder(template.getName())
                              .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                              .returns(templateClass)
                              .addStatement("return new $N()", typeSpec)
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
