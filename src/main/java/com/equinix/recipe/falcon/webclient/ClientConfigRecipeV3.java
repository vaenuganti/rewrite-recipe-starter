package com.equinix.recipe.falcon.webclient;

import com.equinix.falcon.restful.config.rest.reactive.FalconWebClientFactory;
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
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Markers;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;



// Making your recipe immutable helps make them idempotent and eliminates categories of possible bugs.
// Configuring your recipe in this way also guarantees that basic validation of parameters will be done for you by rewrite.
// Also note: All recipes must be serializable. This is verified by RewriteTest.rewriteRun() in your tests.
@Value
@EqualsAndHashCode(callSuper = false)
public class ClientConfigRecipeV3 extends Recipe {


    @Option(displayName = "Fully Qualified Class Name",
            description = "A fully qualified class name indicating which class to add a hello() method to.",
            example = "com.yourorg.FooBar")
    @NonNull
    String fullyQualifiedClassName;

    // All recipes must be serializable. This is verified by RewriteTest.rewriteRun() in your tests.
    @JsonCreator
    public ClientConfigRecipeV3(@NonNull @JsonProperty("fullyQualifiedClassName") String fullyQualifiedClassName) {
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


    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // getVisitor() should always return a new instance of the visitor to avoid any state leaking between cycles
        return new ClientConfigVisitor();
    }

    public class ClientConfigVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final JavaTemplate falconWCTemplate =
                JavaTemplate.builder(
                                "/** OpenRewrite rewrites the class #{}! Import, Uncomment and implement the code where needed **/ \n" +
                                        "// private final FalconWebClientFactory falconWebClientFactory; \n" +
                                        "//  private final  #{}  #{};\n " +
                                "\n  /** public WebClient adminWebClient() { \n" +
                                "        return falconWebClientFactory\n" +
                                "                .getWebClientBuilder(adminRestConfiguration)\n" +
                                "                .build();\n" +
                                "    } **/")
                        .imports("com.equinix.falcon.restful.config.rest.reactive.FalconWebClientFactory")
//                        .javaParser(JavaParser.fromJavaVersion().dependsOn(
//                                "import lombok.RequiredArgsConstructor;\n" +
//                                "import org.springframework.context.annotation.Configuration;\n" +
//                                "@RequiredArgsConstructor\n"+
//                                "@Configuration\n"
//                                )).
                        .build();



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
            /*boolean variableExists = classDecl.getBody().getStatements().stream()
                    .filter(statement -> statement instanceof J.VariableDeclarations)
                    .map(J.VariableDeclarations.class::cast)
                    .map(variableDeclarations -> variableDeclarations.getVariables())
                    .anyMatch(variableDeclaration -> (variableDeclaration.equals("falconWebClientFactory") ||
                            variableDeclaration.equals("adminRestConfiguration")));
*/
            // If the class already has a `falconWebClientFactory` method, don't make any changes to it.
            /*if (variableExists) {
                return classDecl;
            }*/





            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);

            // Remove any existing fields
            /*cd = cd.withBody(cd.getBody().withStatements(cd.getBody().getStatements().stream()
                    .filter(it -> !(it instanceof J.VariableDeclarations))
                    .collect(Collectors.toList())));
*/
            // Add method, fields, static initializer
            // Putting the method first because we're going to move the fields & initializer to the start of the class in the next step
            List<J.Annotation> annotations = classDecl.getAllAnnotations();
            J.Annotation configAnnotation = new J.Annotation(UUID.randomUUID(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    TypeTree.build("Configuration"),
                    null);
            annotations.add(configAnnotation);
            System.out.println(annotations.size());
            cd = cd.withLeadingAnnotations(annotations);


            maybeAddImport("org.springframework.boot.autoconfigure.SpringBootApplication", false);
            maybeAddImport("org.springframework.context.annotation.Import", false);
            cd = cd.withBody(JavaTemplate.builder(
                    "private FalconWebClientFactory falconWebClientFactory = null; \n" +
                            "private static final int DEF_COUNT = 20;\n\n" +
                            "static {\n" +
                            "   System.out.println(falconWebClientFactory);\n" +
                            "}\n"
                    )
                    .contextSensitive()
//                    .javaParser(javaParser())
                    . imports("org.springframework.context.annotation.Configuration")
                    .build()
                    .apply(new Cursor(new Cursor(getCursor().getParent(), cd), cd.getBody()),
                            cd.getBody().getCoordinates().lastStatement()));
            maybeAddImport("org.springframework.context.annotation.Configuration", false);
            maybeAddImport("com.equinix.falcon.restful.config.rest.reactive.FalconWebClientFactory", false);
            maybeAddImport("org.springframework.web.reactive.function.client.WebClient", false);


            // Move the fields and static initializer newly added statements to the beginning of the class body
           /* List<Statement> existingStatements = cd.getBody().getStatements();
            List<Statement> reorderedStatements = Stream.concat(
                    existingStatements.subList(existingStatements.size() - 3, existingStatements.size()).stream(),
                    existingStatements.subList(0, existingStatements.size() - 3).stream()
            ).collect(Collectors.toList());
            cd = cd.withBody(cd.getBody().withStatements(reorderedStatements));*/
            doAfterVisit(new AutoFormatVisitor<>());
            return cd;
            // Interpolate the fullyQualifiedClassName into the template and use the resulting LST to update the class body
            /*classDecl = classDecl.withBody( falconWCTemplate.apply(new Cursor(getCursor(), classDecl.getBody()),
                    classDecl.getBody().getCoordinates().lastStatement(),
                    fullyQualifiedClassName ));
*/
            /*System.out.println(classDecl.getPrefix());
            System.out.println(classDecl.getBody().getCoordinates());
            System.out.println(classDecl.getCoordinates());
            System.out.println(getCursor());
            System.out.println(getCursor().getParent());*/

            /*classDecl = classDecl.withBody( adminRestConfigTemplate.apply(new Cursor(getCursor(), classDecl.getBody()),
                    classDecl.getBody().getCoordinates().lastStatement()));*/

            /*maybeAddImport("org.springframework.web.reactive.function.client.WebClient");

            doAfterVisit(new AutoFormatVisitor<>());

            maybeAddImport("org.springframework.context.annotation.Configuration");
            return classDecl;*/

        }

       /* @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, ExecutionContext ctx) {
            return JavaTemplate.builder("generateRandomAlphanumericString()")
                    .contextSensitive()
                    .javaParser(javaParser())
                    .build().apply(getCursor(), m.getCoordinates().replace());
        }*/


        public J.Annotation visitAnnotation(J.Annotation a, ExecutionContext ctx) {
            /*if (!TypeUtils.isOfClassType(a.getType(), AddOrUpdateAnnotationAttribute.this.annotationType)) {
                return a;
            } else {*/
                /*String newAttributeValue = "\" @Configuration \"";
                List<Expression> currentArgs = a.getArguments();*/
                /*if (currentArgs != null && !currentArgs.isEmpty()) {
                    AtomicBoolean foundAttributeWithDesiredValue = new AtomicBoolean(false);
                    List<Expression> newArgs = ListUtils.map(currentArgs, (it) -> {
                        if (it instanceof J.Assignment) {
                            J.Assignment as = (J.Assignment)it;
                            J.Identifier var = (J.Identifier)as.getVariable();
                            if (AddOrUpdateAnnotationAttribute.this.attributeName != null && AddOrUpdateAnnotationAttribute.this.attributeName.equals(var.getSimpleName())) {
                                J.Literal value = (J.Literal)as.getAssignment();
                                if (newAttributeValue == null) {
                                    return null;
                                } else if (!newAttributeValue.equals(value.getValueSource()) && !Boolean.TRUE.equals(AddOrUpdateAnnotationAttribute.this.addOnly)) {
                                    return as.withAssignment(value.withValue(newAttributeValue).withValueSource(newAttributeValue));
                                } else {
                                    foundAttributeWithDesiredValue.set(true);
                                    return it;
                                }
                            } else {
                                return it;
                            }
                        } else if (it instanceof J.Literal) {
                            if (AddOrUpdateAnnotationAttribute.this.attributeName != null && !"value".equals(AddOrUpdateAnnotationAttribute.this.attributeName)) {
                                return (Expression)((J.Annotation)JavaTemplate.builder("value = #{}").contextSensitive().build().apply(this.getCursor(), a.getCoordinates().replaceArguments(), new Object[]{it})).getArguments().get(0);
                            } else if (newAttributeValue == null) {
                                return null;
                            } else {
                                J.Literal valuex = (J.Literal)it;
                                if (!newAttributeValue.equals(valuex.getValueSource()) && !Boolean.TRUE.equals(AddOrUpdateAnnotationAttribute.this.addOnly)) {
                                    return ((J.Literal)it).withValue(newAttributeValue).withValueSource(newAttributeValue);
                                } else {
                                    foundAttributeWithDesiredValue.set(true);
                                    return it;
                                }
                            }
                        } else {
                            return it;
                        }
                    });
                    if (!foundAttributeWithDesiredValue.get() && newArgs == currentArgs) {
                        String effectiveName = AddOrUpdateAnnotationAttribute.this.attributeName == null ? "value" : AddOrUpdateAnnotationAttribute.this.attributeName;
                        J.Assignment as = (J.Assignment)((J.Annotation)JavaTemplate.builder("#{} = #{}").contextSensitive().build().apply(this.getCursor(), a.getCoordinates().replaceArguments(), new Object[]{effectiveName, newAttributeValue})).getArguments().get(0);
                        List<Expression> newArguments = ListUtils.concat(as, a.getArguments());
                        a = a.withArguments(newArguments);
                        a = (J.Annotation)this.autoFormat(a, ctx);
                        return a;
                    } else {
                        return a.withArguments(newArgs);
                    }
                }*//* else if (newAttributeValue == null) {
                    return a;
                }*/
//            else {
                    return (J.Annotation)JavaTemplate.builder("#{}").contextSensitive().build().apply(this.getCursor(), a.getCoordinates().replaceArguments(), new Object[]{"@Configuration"});
//                }
//            }
        }


    }

}