package com.equinix.recipe.falcon.webclient;

import com.equinix.falcon.restful.config.rest.ServiceConfiguration;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.security.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddClientPathConfigRecipe extends Recipe {


    public static final String WEB_CLIENT_FACTORY_TEMPLATE = "/** OpenRewrite rewrites the class #{}! **/ \n" +
            " @Getter\n" +
            " @Setter\n" /*+
            " private String usersPath;"*/;

    @Option(displayName = "Fully Qualified Class Name",
            description = "A fully qualified class name indicating which class to add a WebClient() method to.",
            example = "com.yourorg.FooBar")
    @NonNull
    String fullyQualifiedClassName;

    @Option(displayName = "Fully Qualified Class Name",
            description = "List of class variables to be added to WebClient()",
            example = "com.yourorg.FooBar")
    @NonNull
    List<String> classVariables = new ArrayList<>();

    // All recipes must be serializable. This is verified by RewriteTest.rewriteRun() in your tests.
    @JsonCreator
    public AddClientPathConfigRecipe(@NonNull @JsonProperty("fullyQualifiedClassName") String fullyQualifiedClassName, @NonNull @JsonProperty("classVariables") List<String> classVariables) {
        this.fullyQualifiedClassName = fullyQualifiedClassName;
        this.classVariables.addAll(classVariables);
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
        return new AddClientPathVisitor();
    }

    public class AddClientPathVisitor extends JavaIsoVisitor<ExecutionContext> {


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
                    .anyMatch(variableDeclaration -> (variableDeclaration.equals("usersPath") ));
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
//            J.Annotation constArgAnnotation = new J.Annotation(UUID.randomUUID(),
//                    Space.EMPTY,
//                    Markers.EMPTY,
//                    TypeTree.build("ConfigurationProperties(prefix = \"admin.api\")"),
//                    null);
//            annotations.add(constArgAnnotation);
            cd = cd.withLeadingAnnotations(annotations);

            StringBuffer variableStringTemplate =  new StringBuffer();
            variableStringTemplate.append("/** OpenRewrite rewrites the class #{}! **/ \n");

            classVariables.forEach(variable -> {variableStringTemplate.append( " @Getter\n" +
                    " @Setter\n" + "private String " + variable +";\n");  });

            System.out.println(variableStringTemplate);
            // Add Body
            cd = cd.withBody(JavaTemplate.builder(
                            String.valueOf(variableStringTemplate))
                    .contextSensitive()
                    .imports("org.springframework.context.annotation.Configuration")
                    .build()
                    .apply(new Cursor(new Cursor(getCursor().getParent(), cd), cd.getBody()),
                            cd.getBody().getCoordinates().lastStatement(), fullyQualifiedClassName));

            // Add Imports
            maybeAddImport("org.springframework.context.annotation.Configuration", false);
            maybeAddImport("org.springframework.boot.context.properties.ConfigurationProperties", false);
            maybeAddImport("lombok.Getter", false);
            maybeAddImport("lombok.Setter", false);
            maybeAddImport("com.equinix.falcon.restful.config.rest.ServiceConfiguration", false);


            // Add Extends
            TypeTree extending = TypeTree.build("ServiceConfiguration");
            cd.withExtends(extending);
            doAfterVisit(new AutoFormatVisitor<>());
            return cd;


        }
    }

}
