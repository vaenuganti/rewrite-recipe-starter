package com.equinix.recipe.falcon.webclient;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Markers;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = false)
public class FalconWebClientFactoryRecipe extends Recipe {


    public static final String WEB_CLIENT_FACTORY_TEMPLATE = "/** OpenRewrite rewrites the class #{}! **/ \n" +
            "private final FalconWebClientFactory falconWebClientFactory; \n" +
            "private final AdminRestConfiguration adminRestConfiguration; \n"+
            "@Bean(name = \"#{}WebClient\") " +
            "\n  public WebClient #{}WebClient() { \n" +
            "        return falconWebClientFactory\n" +
            "                .getWebClientBuilder(#{}RestConfiguration)\n" +
            "                .build();\n" +
            "    }";
    @Option(displayName = "Fully Qualified Class Name",
            description = "A fully qualified class name indicating which class to add a WebClient() method to.",
            example = "com.yourorg.FooBar")
    @NonNull
    String fullyQualifiedClassName;

    // All recipes must be serializable. This is verified by RewriteTest.rewriteRun() in your tests.
    @JsonCreator
    public FalconWebClientFactoryRecipe(@NonNull @JsonProperty("fullyQualifiedClassName") String fullyQualifiedClassName) {
        this.fullyQualifiedClassName = fullyQualifiedClassName;
    }

    @Override
    public String getDisplayName() {
        return "WebClient Factory Configuration";
    }

    @Override
    public String getDescription() {
        return "Adds a Webclient() with Falcon Factory configuration to the specified class.";
    }


    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // getVisitor() should always return a new instance of the visitor to avoid any state leaking between cycles
        return new WebClientFactoryVisitor();
    }

    public class WebClientFactoryVisitor extends JavaIsoVisitor<ExecutionContext> {


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
            // If the class already has a `falconWebClientFactory` method, don't make any changes to it.
            if (variableExists) {
                return classDecl;
            }

            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);

            // Remove any existing fields
            cd = cd.withBody(cd.getBody().withStatements(cd.getBody().getStatements().stream()
                    .filter(it -> !(it instanceof J.VariableDeclarations))
                    .collect(Collectors.toList())));

            //Add Annotations

            List<J.Annotation> annotations = classDecl.getAllAnnotations();
            J.Annotation configAnnotation = new J.Annotation(UUID.randomUUID(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    TypeTree.build("Configuration"),
                    null);
            annotations.add(configAnnotation);
            J.Annotation constArgAnnotation = new J.Annotation(UUID.randomUUID(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    TypeTree.build("RequiredArgsConstructor"),
                    null);
            annotations.add(constArgAnnotation);
            cd = cd.withLeadingAnnotations(annotations);


            //Add Body
            cd = cd.withBody(JavaTemplate.builder(
                            WEB_CLIENT_FACTORY_TEMPLATE)
                    .contextSensitive()
                    .imports("org.springframework.context.annotation.Configuration")
                    .build()
                    .apply(new Cursor(new Cursor(getCursor().getParent(), cd), cd.getBody()),
                            cd.getBody().getCoordinates().lastStatement(), fullyQualifiedClassName, "admin", "admin","admin"));

            // Add imports
            maybeAddImport("org.springframework.context.annotation.Configuration", false);
            maybeAddImport("com.equinix.falcon.restful.config.rest.reactive.FalconWebClientFactory", false);
            maybeAddImport("org.springframework.web.reactive.function.client.WebClient", false);
            maybeAddImport("lombok.RequiredArgsConstructor", false);
            maybeAddImport("org.springframework.context.annotation.Bean", false);


            doAfterVisit(new AutoFormatVisitor<>());
            return cd;


        }
    }

}
