package com.equinix.recipe.falcon.webclient;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


// Making your recipe immutable helps make them idempotent and eliminates categories of possible bugs.
// Configuring your recipe in this way also guarantees that basic validation of parameters will be done for you by rewrite.
// Also note: All recipes must be serializable. This is verified by RewriteTest.rewriteRun() in your tests.
@Value
@EqualsAndHashCode(callSuper = false)
public class ClientConfigRecipe extends Recipe {
    @Option(displayName = "Fully Qualified Class Name",
            description = "A fully qualified class name indicating which class to add a hello() method to.",
            example = "com.yourorg.FooBar")
    @NonNull
    String fullyQualifiedClassName;

    // All recipes must be serializable. This is verified by RewriteTest.rewriteRun() in your tests.
    @JsonCreator
    public ClientConfigRecipe(@NonNull @JsonProperty("fullyQualifiedClassName") String fullyQualifiedClassName) {
        this.fullyQualifiedClassName = fullyQualifiedClassName;
    }

    @Override
    public String getDisplayName() {
        return "Say Hello";
    }

    @Override
    public String getDescription() {
        return "Adds a \"hello\" method to the specified class.";
    }
    private JavaParser.Builder<?, ?> javaParser() {
        return JavaParser.fromJavaVersion()
                .dependsOn(Arrays.asList(
                        Parser.Input.fromString(
                                "package org.apache.commons.lang;\n" +
                                        "import java.util.Random;\n" +
                                        "public class RandomStringUtils {\n" +
                                        "  public static String random(int count, int start, int end, boolean letters, boolean numbers, char[] chars, Random random) {}\n" +
                                        "}\n"),
                        Parser.Input.fromString(
                                "package org.apache.commons.lang3;\n" +
                                        "import java.util.Random;\n" +
                                        "public class RandomStringUtils {\n" +
                                        "  public static String random(int count, int start, int end, boolean letters, boolean numbers, char[] chars, Random random) {}\n" +
                                        "}\n"
                        )));
    }


    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // getVisitor() should always return a new instance of the visitor to avoid any state leaking between cycles
        return  new ClientConfigVisitor();
    }

    public class ClientConfigVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final JavaTemplate falconWCTemplate =
                JavaTemplate.builder( "private final FalconWebClientFactory falconWebClientFactory;" +
                                "/** OpenRewrite rewrites the class #{}! **/ \n" +
                                "\n  public WebClient adminWebClient() { \n" +
                                "        return falconWebClientFactory\n" +
                                "                .getWebClientBuilder(adminRestConfiguration)\n" +
                                "                .build();\n" +
                                "    }")
                        .imports("com.equinix.falcon.restful.config.rest.reactive.FalconWebClientFactory")
                        .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                "import lombok.RequiredArgsConstructor;\n" +
                                "import org.springframework.context.annotation.Configuration;\n" +
                                "@RequiredArgsConstructor\n"+
                                "@Configuration\n"
                                )).
                        build();

        private final JavaTemplate adminRestConfigTemplate =
                JavaTemplate.builder( "private final AdminRestConfiguration adminRestConfiguration;")
                        .build();



        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            // Don't make changes to classes that don't match the fully qualified name
            if (classDecl.getType() == null || !classDecl.getType().getFullyQualifiedName().equals(fullyQualifiedClassName)) {
                return classDecl;
            }

            // Check if the class already has a variable named "webClient".
            boolean variableExists = classDecl.getBody().getStatements().stream()
                    .filter(statement -> statement instanceof J.VariableDeclarations)
                    .map(J.VariableDeclarations.class::cast)
                    .map(variableDeclarations -> variableDeclarations.getVariables())
                    .anyMatch(variableDeclaration -> (variableDeclaration.equals("falconWebClientFactory") ||
                            variableDeclaration.equals("adminRestConfiguration")));
            // If the class already has a `hello()` method, don't make any changes to it.
            if (variableExists) {
                return classDecl;
            }





//            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);

            // Remove any existing fields
           /* cd = cd.withBody(cd.getBody().withStatements(cd.getBody().getStatements().stream()
                    .filter(it -> !(it instanceof J.VariableDeclarations))
                    .collect(Collectors.toList())));*/

            // Add method, fields, static initializer
            // Putting the method first because we're going to move the fields & initializer to the start of the class in the next step
            /*cd = cd.withBody(JavaTemplate.builder("private static String generateRandomAlphanumericString() {\n" +
                            "    return RandomStringUtils.random(DEF_COUNT, 0, 0, true, true, null, SECURE_RANDOM);\n" +
                            "}\n" +
                            "private static final SecureRandom SECURE_RANDOM = new SecureRandom();\n" +
                            "private static final int DEF_COUNT = 20;\n\n" +
                            "static {\n" +
                            "    SECURE_RANDOM.nextBytes(new byte[64]);\n" +
                            "}\n"
                    )
                    .contextSensitive()
                    .javaParser(javaParser())
                    .imports("java.security.SecureRandom")
                    .build()
                    .apply(new Cursor(new Cursor(getCursor().getParent(), cd), cd.getBody()),
                            cd.getBody().getCoordinates().lastStatement()));
            maybeAddImport("java.security.SecureRandom");*/

            // Move the fields and static initializer newly added statements to the beginning of the class body
            /*List<Statement> existingStatements = cd.getBody().getStatements();
            List<Statement> reorderedStatements = Stream.concat(
                    existingStatements.subList(existingStatements.size() - 3, existingStatements.size()).stream(),
                    existingStatements.subList(0, existingStatements.size() - 3).stream()
            ).collect(Collectors.toList());
            cd = cd.withBody(cd.getBody().withStatements(reorderedStatements));

            return cd;*/
            // Interpolate the fullyQualifiedClassName into the template and use the resulting LST to update the class body
            classDecl = classDecl.withBody( JavaTemplate.builder( "private final FalconWebClientFactory falconWebClientFactory; \n" +
                    "private final AdminRestConfiguration adminRestConfiguration; \n" +
                            "/** OpenRewrite rewrites the class #{}! **/ \n" +
                            "\n  public void adminWebClient() { \n" +
                            "         // return falconWebClientFactory\n" +
                            "         //       .getWebClientBuilder(adminRestConfiguration)\n" +
                            "         //       .build();\n" +
                            "    }"
                    )
                    .imports("com.equinix.falcon.restful.config.rest.reactive.FalconWebClientFactory")
                    /*.javaParser(JavaParser.fromJavaVersion().dependsOn(
                            "import lombok.RequiredArgsConstructor;\n" +
                                    "import org.springframework.context.annotation.Configuration;\n" +
                                    "@RequiredArgsConstructor\n"+
                                    "@Configuration\n"
                    ))*/
                    .contextSensitive()
                    .javaParser(javaParser())
                    .imports("com.equinix.falcon.restful.config.rest.reactive.FalconWebClientFactory")
                    .build()
                    .apply(new Cursor(new Cursor(getCursor().getParent(), classDecl), classDecl.getBody()),
                            classDecl.getBody().getCoordinates().lastStatement(),
                    fullyQualifiedClassName));
                   /* .build().apply(new Cursor(getCursor(), classDecl.getBody()),
                    classDecl.getBody().getCoordinates().lastStatement(),
                    fullyQualifiedClassName ));*/

            maybeAddImport("com.equinix.falcon.restful.config.rest.reactive.FalconWebClientFactory");
//            maybeAddImport("org.springframework.web.reactive.function.client.WebClient");
            /*System.out.println(classDecl.getPrefix());
            System.out.println(classDecl.getBody().getCoordinates());
            System.out.println(classDecl.getCoordinates());
            System.out.println(getCursor());
            System.out.println(getCursor().getParent());*/

        /*    classDecl = classDecl.withBody( adminRestConfigTemplate.apply(new Cursor(getCursor(), classDecl.getBody()),
                    classDecl.getBody().getCoordinates().lastStatement()));*/

            /*maybeAddImport("org.springframework.web.reactive.function.client.WebClient");

            doAfterVisit(new AutoFormatVisitor<>());

            maybeAddImport("org.springframework.context.annotation.Configuration");*/
            return classDecl;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, ExecutionContext ctx) {
            return JavaTemplate.builder("generateRandomAlphanumericString()")
                    .contextSensitive()
                    .javaParser(javaParser())
                    .build().apply(getCursor(), m.getCoordinates().replace());
        }





    }
}