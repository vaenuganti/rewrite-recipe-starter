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


// Making your recipe immutable helps make them idempotent and eliminates categories of possible bugs.
// Configuring your recipe in this way also guarantees that basic validation of parameters will be done for you by rewrite.
// Also note: All recipes must be serializable. This is verified by RewriteTest.rewriteRun() in your tests.
@Value
@EqualsAndHashCode(callSuper = false)
public class ClientRecipe extends Recipe {


    public static final String WEB_CLIENT_VARIABLE_TEMPLATE = "/** OpenRewrite rewrites the class #{}! Import, Uncomment and implement the code where needed **/ " +
            "  @Autowired\n" +
            "    private AdminRestConfiguration config;\n" +
            "\n" +
            "    @Autowired\n" +
            "    @Qualifier(\"adminWebClient\")\n" +
            "    private WebClient webClient;\n" +
            "    \n" +
            "    @Autowired\n" +
            "    private FalconRequestContext falconRequestContext;\n" +
            "    \n" ;

    public static final String WEBCLIENT_GET_TEMPLATE = "public Mono<AddModelClassName> getUserProfile(String parameterName) throws FalconAppException {\n" +
            "  // TODO: Implement the  mono with sample suggestion here  \n" +
            "      /**  \n" +
            "        falconRequestContext.currentRequest()\n" +
            "                .addRequestParams(FalconRequest.FalconRequestKey.AUTHORIZATION, authBearer);\n" +
            "        return webClient.get()\n" +
            "                .uri(uriBuilder ->\n" +
            "                        uriBuilder.path(config.getXxxPath())\n" +
            "                                .build(userName))\n" +
            "                .retrieve()\n" +
            "                .bodyToMono(AddModelClassName.class)\n" +
            "                .onErrorMap(throwable -> falconWebClientUtil.getFalconRuntimeException(throwable, this.getClass().getName()));\n" +
            "   **/" +
            " return null; }" ;

    @Option(displayName = "Fully Qualified Class Name",
            description = "A fully qualified class name indicating which class to add a WebClient() method to.",
            example = "com.yourorg.FooBar")
    @NonNull
    String fullyQualifiedClassName;

    // All recipes must be serializable. This is verified by RewriteTest.rewriteRun() in your tests.
    @JsonCreator
    public ClientRecipe(@NonNull @JsonProperty("fullyQualifiedClassName") String fullyQualifiedClassName) {
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
        return new WebClientVisitor();
    }

    public class WebClientVisitor extends JavaIsoVisitor<ExecutionContext> {


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
                    TypeTree.build("Repository"),
                    null);
            annotations.add(configAnnotation);

            cd = cd.withLeadingAnnotations(annotations);

            // Add Body
            cd = cd.withBody(JavaTemplate.builder(
                            WEB_CLIENT_VARIABLE_TEMPLATE + WEBCLIENT_GET_TEMPLATE)
                    .contextSensitive()
                    .imports("org.springframework.context.annotation.Configuration")
                    .build()
                    .apply(new Cursor(new Cursor(getCursor().getParent(), cd), cd.getBody()),
                            cd.getBody().getCoordinates().lastStatement(), fullyQualifiedClassName));

            // Add Imports
            maybeAddImport("com.equinix.falcon.restful.model.FalconRequestContext", false);
            maybeAddImport("org.springframework.web.reactive.function.client.WebClient", false);
            maybeAddImport("com.equinix.uecp.po.util.config.AdminRestConfiguration", false);
            maybeAddImport("org.springframework.stereotype.Repository", false);

            maybeAddImport("org.springframework.beans.factory.annotation.Autowired", false);
            maybeAddImport("org.springframework.beans.factory.annotation.Qualifier", false);
            maybeAddImport("reactor.core.publisher.Mono", false);
            maybeAddImport("com.equinix.falcon.exception.throwable.FalconAppException", false);


            doAfterVisit(new AutoFormatVisitor<>());
            return cd;


        }
    }

}