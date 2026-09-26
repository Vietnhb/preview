package com.example.backend.matter;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.ArrayLiteral;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.Block;
import org.mozilla.javascript.ast.EmptyStatement;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.FunctionCall;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.InfixExpression;
import org.mozilla.javascript.ast.KeywordLiteral;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.NumberLiteral;
import org.mozilla.javascript.ast.ObjectLiteral;
import org.mozilla.javascript.ast.ObjectProperty;
import org.mozilla.javascript.ast.ParenthesizedExpression;
import org.mozilla.javascript.ast.PropertyGet;
import org.mozilla.javascript.ast.ReturnStatement;
import org.mozilla.javascript.ast.StringLiteral;
import org.mozilla.javascript.ast.UnaryExpression;
import org.mozilla.javascript.ast.VariableDeclaration;
import org.mozilla.javascript.ast.VariableInitializer;

/** A deliberately small executable grammar, checked with a real JavaScript AST. */
public final class MatterCodeSafety {
    private static final int MAX_CODE_LENGTH = 120_000;
    private static final String MATTER_ENGINE_CREATE = "Matter.Engine.create";
    private static final String PARAMS_PREFIX = "params.";
    private static final Set<String> BLOCKED_PROPERTIES = Set.of(
            "constructor", "prototype", "__proto__", "caller", "callee", "arguments",
            "apply", "call", "bind", "eval", "toString", "valueOf");
    private static final Set<String> MATTER_CALLS = Set.of(
            MATTER_ENGINE_CREATE, "Matter.Bodies.rectangle", "Matter.Bodies.circle",
            "Matter.Body.setVelocity",
            "Matter.Body.setAngularVelocity", "Matter.Body.setPosition", "Matter.Body.setAngle",
            "Matter.Body.setMass",
            "Matter.Body.setStatic", "Matter.Body.applyForce", "Matter.Composite.add",
            "Matter.Composite.remove", "Matter.World.add", "Matter.World.remove",
            "Matter.Constraint.create");
    private static final Set<String> MATH_CALLS = Set.of(
            "Math.abs", "Math.min", "Math.max", "Math.sqrt",
            "Math.sin", "Math.cos", "Math.tan", "Math.atan2", "Math.floor", "Math.ceil");
    private static final Set<String> BUILTINS = Set.of("Matter", "Math", "params", "width", "height");

    private MatterCodeSafety() { }

    public static void verify(String code, Set<String> parameterNames) {
        if (code == null || code.isBlank() || code.length() > MAX_CODE_LENGTH) {
            throw new UnsafeCodeException("Generated code is empty or too large");
        }
        if (parameterNames == null) parameterNames = Set.of();
        final Set<String> parameters = Set.copyOf(parameterNames);
        var compiler = new CompilerEnvirons();
        compiler.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);
        compiler.setStrictMode(true);
        AstRoot root;
        try {
            root = new Parser(compiler).parse(
                    "function __physlive(Matter, params, width, height) {\n" + code + "\n}",
                    "generated-matter.js", 1);
        } catch (RuntimeException failure) {
            throw new UnsafeCodeException("Generated code has invalid JavaScript syntax");
        }
        if (root.getStatements().size() != 1 || !(root.getStatements().getFirst() instanceof FunctionNode function)) {
            throw new UnsafeCodeException("Generated code must be a function body");
        }
        Set<String> locals = new HashSet<>();
        List<AstNode> statements = new ArrayList<>();
        for (org.mozilla.javascript.Node child = function.getBody().getFirstChild();
                child != null; child = child.getNext()) {
            if (!(child instanceof EmptyStatement)) statements.add((AstNode) child);
        }
        if (statements.isEmpty() || !(statements.getLast() instanceof ReturnStatement)) {
            throw new UnsafeCodeException("Generated code must end by returning the engine");
        }
        for (AstNode statement : statements) {
            if (!(statement instanceof VariableDeclaration || statement instanceof ExpressionStatement
                    || statement instanceof ReturnStatement)) {
                throw new UnsafeCodeException("Generated code contains an unsupported statement");
            }
            if (statement instanceof VariableDeclaration declaration) {
                if (declaration.getType() != Token.CONST && declaration.getType() != Token.LET) {
                    throw new UnsafeCodeException("Only const and let declarations are permitted");
                }
                for (VariableInitializer item : declaration.getVariables()) {
                    if (!(item.getTarget() instanceof Name name) || item.getInitializer() == null
                            || BUILTINS.contains(name.getIdentifier()) || !locals.add(name.getIdentifier())) {
                        throw new UnsafeCodeException("Variable declarations must have unique names and values");
                    }
                }
            }
        }
        AstNode returned = ((ReturnStatement) statements.getLast()).getReturnValue();
        if (!(returned instanceof Name returnedName) || !locals.contains(returnedName.getIdentifier())) {
            throw new UnsafeCodeException("Generated code must return a locally created engine");
        }
        boolean engineReturn = statements.stream().filter(VariableDeclaration.class::isInstance)
                .map(VariableDeclaration.class::cast).flatMap(item -> item.getVariables().stream())
                .anyMatch(item -> item.getTarget() instanceof Name local
                        && local.getIdentifier().equals(returnedName.getIdentifier())
                        && item.getInitializer() instanceof FunctionCall call
                        && MATTER_ENGINE_CREATE.equals(staticPath(call.getTarget())));
        if (!engineReturn) throw new UnsafeCodeException("Returned value must be the created engine");
        Set<String> finalLocals = Set.copyOf(locals);
        Set<String> bodyLocals = new HashSet<>();
        statements.stream().filter(VariableDeclaration.class::isInstance)
                .map(VariableDeclaration.class::cast).flatMap(item -> item.getVariables().stream())
                .forEach(item -> {
                    if (item.getTarget() instanceof Name bodyName
                            && item.getInitializer() instanceof FunctionCall call
                            && staticPath(call.getTarget()).startsWith("Matter.Bodies.")) {
                        bodyLocals.add(bodyName.getIdentifier());
                    }
                });
        final String engineName = returnedName.getIdentifier();
        final int[] calls = {0};
        final int[] bodies = {0};
        function.getBody().visit(node -> {
            if (!allowedNode(node)) throw new UnsafeCodeException("Generated code contains unsupported syntax");
            if (node instanceof FunctionCall call) {
                checkCall(call);
                if (++calls[0] > 400) throw new UnsafeCodeException("Generated code has too many API calls");
                if (staticPath(call.getTarget()).startsWith("Matter.Bodies.") && ++bodies[0] > 80) {
                    throw new UnsafeCodeException("Generated code creates too many bodies");
                }
            }
            if (node instanceof PropertyGet property) {
                checkProperty(property, parameters, finalLocals, engineName, bodyLocals);
            }
            if (node instanceof ObjectProperty property) checkObjectKey(property);
            if (node instanceof InfixExpression expression && !(node instanceof PropertyGet)
                    && !(node instanceof ObjectProperty)) checkOperator(expression);
            if (node instanceof NumberLiteral literal) {
                double number = literal.getNumber();
                if (!Double.isFinite(number) || Math.abs(number) > 1_000_000) {
                    throw new UnsafeCodeException("Generated code contains an excessive numeric literal");
                }
            }
            if (node instanceof KeywordLiteral keyword && keyword.getType() != Token.TRUE
                    && keyword.getType() != Token.FALSE && keyword.getType() != Token.NULL) {
                throw new UnsafeCodeException("Generated code contains a forbidden keyword");
            }
            if (node instanceof UnaryExpression expression && expression.getType() != Token.NEG
                    && expression.getType() != Token.POS) {
                throw new UnsafeCodeException("Generated code contains an unsupported unary operator");
            }
            if (node instanceof Name name) checkName(name, finalLocals);
            return true;
        });
        long engineCreations = countCall(function.getBody(), MATTER_ENGINE_CREATE);
        if (engineCreations != 1) throw new UnsafeCodeException("Generated code must create exactly one engine");
    }

    private static boolean allowedNode(AstNode node) {
        return node instanceof Block || node instanceof VariableDeclaration || node instanceof VariableInitializer
                || node instanceof EmptyStatement || node instanceof ExpressionStatement
                || node instanceof ReturnStatement
                || node instanceof FunctionCall || node instanceof PropertyGet || node instanceof ObjectLiteral
                || node instanceof ObjectProperty || node instanceof ArrayLiteral || node instanceof NumberLiteral
                || node instanceof StringLiteral || node instanceof KeywordLiteral || node instanceof Name
                || node instanceof InfixExpression || node instanceof UnaryExpression
                || node instanceof ParenthesizedExpression;
    }

    private static void checkCall(FunctionCall call) {
        String path = staticPath(call.getTarget());
        if (!MATTER_CALLS.contains(path) && !MATH_CALLS.contains(path)) {
            throw new UnsafeCodeException("Generated code calls an API outside the allowlist");
        }
    }

    private static void checkProperty(PropertyGet property, Set<String> parameters, Set<String> locals,
            String engineName, Set<String> bodyLocals) {
        String key = property.getProperty().getIdentifier();
        if (BLOCKED_PROPERTIES.contains(key)) throw new UnsafeCodeException("Generated code uses a forbidden property");
        String path = staticPath(property);
        if (path.startsWith(PARAMS_PREFIX)
                && (path.indexOf('.', PARAMS_PREFIX.length()) >= 0 || !parameters.contains(key))) {
            throw new UnsafeCodeException("Generated code uses an undeclared parameter");
        }
        if (path.startsWith("Matter.") && !(path.equals("Matter.Engine")
                || path.equals("Matter.Bodies") || path.equals("Matter.Body")
                || path.equals("Matter.Composite") || path.equals("Matter.World")
                || path.equals("Matter.Constraint") || MATTER_CALLS.contains(path))) {
            throw new UnsafeCodeException("Generated code accesses a Matter API outside the allowlist");
        }
        if (path.startsWith("Math.") && !MATH_CALLS.contains(path)
                && !path.equals("Math.PI")) {
            throw new UnsafeCodeException("Generated code accesses a Math member outside the allowlist");
        }
        String root = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;
        if (!BUILTINS.contains(root) && !locals.contains(root)) {
            throw new UnsafeCodeException("Generated code accesses an unknown object");
        }
        if (root.equals(engineName)) {
            String suffix = path.substring(root.length());
            if (!Set.of(".world", ".world.gravity", ".world.gravity.x", ".world.gravity.y",
                    ".world.gravity.scale", ".gravity", ".gravity.x", ".gravity.y",
                    ".gravity.scale").contains(suffix)) {
                throw new UnsafeCodeException("Generated code accesses an unsupported engine property");
            }
        } else if (bodyLocals.contains(root)) {
            String suffix = path.substring(root.length());
            if (!Set.of(".position", ".position.x", ".position.y", ".velocity",
                    ".velocity.x", ".velocity.y", ".mass", ".angle", ".angularVelocity")
                    .contains(suffix)) {
                throw new UnsafeCodeException("Generated code accesses an unsupported body property");
            }
        } else if (locals.contains(root)) {
            throw new UnsafeCodeException("Generated code accesses an unsupported local property");
        }
    }

    private static void checkObjectKey(ObjectProperty property) {
        AstNode left = property.getLeft();
        String key = switch (left) {
            case Name name -> name.getIdentifier();
            case StringLiteral literal -> literal.getValue();
            default -> "";
        };
        if (key.isBlank() || BLOCKED_PROPERTIES.contains(key)) {
            throw new UnsafeCodeException("Generated code uses an unsafe object key");
        }
    }

    private static void checkOperator(InfixExpression expression) {
        int type = expression.getType();
        if (type != Token.ADD && type != Token.SUB && type != Token.MUL && type != Token.DIV
                && type != Token.MOD && type != Token.ASSIGN && type != Token.COLON) {
            throw new UnsafeCodeException("Generated code uses an unsupported operator");
        }
        if (type == Token.ASSIGN) {
            AstNode left = expression.getLeft();
            switch (left) {
            case Name name when BUILTINS.contains(name.getIdentifier()) ->
                    throw new UnsafeCodeException("Generated code may not overwrite inputs");
            case Name name -> { /* Local assignments are allowed. */ }
            case PropertyGet property -> {
                String path = staticPath(property);
                if (path.startsWith("Matter.") || path.startsWith("Math.") || path.startsWith(PARAMS_PREFIX)) {
                    throw new UnsafeCodeException("Generated code may not overwrite provider APIs or parameters");
                }
            }
            default -> throw new UnsafeCodeException("Generated code has an invalid assignment target");
            }
        }
    }

    private static void checkName(Name name, Set<String> locals) {
        AstNode parent = name.getParent();
        if (parent instanceof PropertyGet property && property.getProperty() == name) return;
        if (parent instanceof ObjectProperty property && property.getLeft() == name) return;
        if (parent instanceof VariableInitializer initializer && initializer.getTarget() == name) return;
        if (!BUILTINS.contains(name.getIdentifier()) && !locals.contains(name.getIdentifier())) {
            throw new UnsafeCodeException("Generated code reads an undeclared name");
        }
    }

    private static String staticPath(AstNode node) {
        if (node instanceof Name name) return name.getIdentifier();
        if (node instanceof PropertyGet property) {
            return staticPath(property.getTarget()) + "." + property.getProperty().getIdentifier();
        }
        throw new UnsafeCodeException("Generated code uses dynamic property access");
    }

    private static long countCall(AstNode body, String path) {
        final long[] count = {0};
        body.visit(node -> {
            if (node instanceof FunctionCall call && path.equals(staticPath(call.getTarget()))) count[0]++;
            return true;
        });
        return count[0];
    }

    public static class UnsafeCodeException extends RuntimeException {
        public UnsafeCodeException(String message) { super(message); }
    }
}
