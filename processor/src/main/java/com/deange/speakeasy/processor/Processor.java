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
import java.util.Collection;
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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static com.deange.speakeasy.processor.Processor.OPTION_KEY;

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
        for (final String resDir : resDirs) {
            final File folder = new File(resDir);
            if (folder.exists()) {
                processFolder(folder);
            }
        }

        generateTemplatesJavaFile();

        return true;
    }

    private void processFolder(final File resDir) {
        final Collection<File> files = FileUtils.listFiles(resDir, new String[]{"xml"}, true);
        for (final File xmlFile : files) {
            try {
                processFile(xmlFile);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void processFile(final File xmlFile) throws Exception {
        final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        final Document document = builder.parse(xmlFile);

        final NodeList nodes = document.getElementsByTagName("string");
        for (int i = 0; i < nodes.getLength(); ++i) {
            final Element node = (Element) nodes.item(i);
            processString(node);
        }
    }

    private void processString(final Element node) {
        final String resName = node.getAttribute("name");
        final String resValue = node.getTextContent();

        final Template template = Template.parse(resName, resValue);

        final String alias = node.getAttribute("alias");
        if (!StringUtils.isEmpty(alias)) {
            template.setAlias(alias);
        }

        if (template.isValidTemplate()) {
            mTemplates.put(resName, template);
        }
    }

    private TypeSpec generateClassForTemplate(
            final String resName,
            final Template template,
            final TypeSpec... interfaces) {

        final String className = StringUtils.snakeCaseToCamelCase(resName, true);
        final ClassName clazz = ClassName.get(PACKAGE_NAME, className);

        final String original = template.value;
        final List<String> fields = template.getFields();

        final TypeSpec.Builder builder = TypeSpec.classBuilder(className);
        builder.addModifiers(Modifier.PUBLIC);
        builder.addSuperinterfaces(
                Arrays.stream(interfaces)
                      .map(superInterface -> ClassName.get(PACKAGE_NAME, superInterface.name))
                      .collect(Collectors.toList()));

        // Package-private constructor
        builder.addMethod(MethodSpec.constructorBuilder().build());

        for (final String fieldName : fields) {
            builder.addField(
                    FieldSpec.builder(String.class, fieldName).addModifiers(Modifier.PRIVATE)
                             .build()
            );

            builder.addMethod(
                    MethodSpec.methodBuilder(fieldName)
                              .addModifiers(Modifier.PUBLIC)
                              .addParameter(String.class, fieldName)
                              .returns(clazz)
                              .addStatement("this.$N = $N", fieldName, fieldName)
                              .addStatement("return this")
                              .build()
            );
        }

        final MethodSpec.Builder buildBuilder =
                MethodSpec.methodBuilder("build")
                          .addAnnotation(Annotations.OVERRIDE)
                          .addModifiers(Modifier.PUBLIC)
                          .returns(String.class);

        if (fields.isEmpty()) {
            // No fields in the string value
            buildBuilder.addStatement("return $S", original);

        } else {
            buildBuilder.addCode("return new $T()", StringBuilder.class);
            template.getParts().forEach(part -> part.appendValue(buildBuilder));
            buildBuilder.addCode(".toString();\n");
        }

        builder.addMethod(buildBuilder.build());

        return builder.build();
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
                    MethodSpec.methodBuilder(template.getMethodName())
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
