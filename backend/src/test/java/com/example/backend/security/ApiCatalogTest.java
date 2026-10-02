package com.example.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.MappedSuperclass;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.repository.Repository;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Inventories compiled Spring mappings and verifies their actual HTTP role gates without a database. */
@SpringJUnitWebConfig(RoleAuthorizationTest.Config.class)
class ApiCatalogTest {
    private static final String BASE = "com.example.backend.";
    private static final Set<String> FEATURES = Set.of("account", "assignment", "curriculum", "library",
            "operations", "physics", "problem", "realtime", "reviewer", "school", "simulation", "support");
    private static final Set<String> MVC_LAYERS = Set.of("controller", "service", "repository", "dto", "mapper");

    @Test
    void mvcTypesAndDependenciesStayInTheirLayers() throws Exception {
        Path classes = Path.of("target/classes/com/example/backend");
        try (var files = Files.walk(classes)) {
            for (var file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = BASE + classes.relativize(file).toString().replace('\\', '.').replace('/', '.')
                        .replaceAll("\\.class$", "");
                Class<?> type = Class.forName(name, false, getClass().getClassLoader());
                layerOf(name);
                if (type.isAnnotationPresent(RestController.class) || type.isAnnotationPresent(RestControllerAdvice.class))
                    assertLayer(type, "controller");
                if (type.isAnnotationPresent(Service.class)) assertLayer(type, "service");
                if (Repository.class.isAssignableFrom(type)) assertLayer(type, "repository");
                if (type.isAnnotationPresent(Entity.class) || type.isAnnotationPresent(MappedSuperclass.class))
                    assertLayer(type, "entity");
                if (type.isEnum() && type.getDeclaringClass() == null) assertLayer(type, "enums");
                if (type.isAnnotationPresent(ConfigurationProperties.class)) assertLayer(type, "config");
                if (Throwable.class.isAssignableFrom(type)) assertLayer(type, "exception");
                for (var field : type.getDeclaredFields()) assertDependency(type, field.getType().getName());
                for (var constructor : type.getDeclaredConstructors())
                    for (var input : constructor.getParameterTypes()) assertDependency(type, input.getName());
                for (var method : type.getDeclaredMethods()) {
                    assertDependency(type, method.getReturnType().getName());
                    for (var input : method.getParameterTypes()) assertDependency(type, input.getName());
                }
                try (var bytecode = Files.newInputStream(file)) {
                    new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(int access, String method, String descriptor,
                                                         String signature, String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override public void visitTypeInsn(int opcode, String target) { check(target); }
                                @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) { check(owner); }
                                @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) { check(owner); }
                                private void check(String target) { assertDependency(type, target.replace('/', '.')); }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
    }

    private void assertLayer(Class<?> type, String layer) {
        assertEquals(layer, layerOf(type.getName()), "Wrong MVC layer for " + type.getName());
    }

    private String layerOf(String name) {
        if (!name.startsWith(BASE)) return "external";
        if (name.equals(BASE + "Application") || name.startsWith(BASE + "Application$")) return "application";
        if (name.lastIndexOf('.') < BASE.length())
            throw new AssertionError("Runtime class outside the agreed MVC modules: " + name);
        String relativePackage = name.substring(BASE.length(), name.lastIndexOf('.'));
        if (Set.of("config", "security", "bootstrap", "exception").contains(relativePackage)) return relativePackage;
        String[] segments = relativePackage.split("\\.");
        if (segments.length >= 3 && segments[0].equals("base")) {
            String layer = String.join(".", Arrays.copyOfRange(segments, 2, segments.length));
            if (segments[1].equals("crud") && Set.of("dto", "model.entity", "model.enums").contains(layer))
                return layer.substring(layer.lastIndexOf('.') + 1);
            if (segments[1].equals("web") && Set.of("controller", "dto").contains(layer)) return layer;
        }
        if (segments.length >= 3 && segments[0].equals("system") && FEATURES.contains(segments[1])) {
            String layer = String.join(".", Arrays.copyOfRange(segments, 2, segments.length));
            if (MVC_LAYERS.contains(layer)) return layer;
            if (Set.of("model.entity", "model.enums").contains(layer))
                return layer.substring(layer.lastIndexOf('.') + 1);
            if (Set.of("school", "simulation").contains(segments[1])
                    && Set.of("dataio.controller", "dataio.service", "dataio.dto").contains(layer))
                return layer.substring(layer.lastIndexOf('.') + 1);
        }
        if (relativePackage.equals("integration.ai")) return "integration";
        throw new AssertionError("Runtime class outside the agreed MVC modules: " + name);
    }

    private void assertDependency(Class<?> owner, String target) {
        String layer = layerOf(owner.getName());
        String targetLayer = layerOf(target);
        Set<String> forbidden = switch (layer) {
            case "controller" -> Set.of("repository");
            case "service" -> Set.of("controller");
            case "repository", "mapper", "dto" -> Set.of("controller", "service");
            case "entity", "enums" -> Set.of("controller", "service", "repository", "dto", "mapper", "integration");
            case "integration" -> Set.of("controller", "service", "repository", "entity");
            default -> Set.of();
        };
        assertFalse(forbidden.contains(targetLayer),
                "MVC dependency points to an upper layer: " + owner.getName() + " -> " + target);
        if (Set.of("controller", "mapper", "dto", "integration").contains(layer))
            assertFalse(target.equals(EntityManager.class.getName()) || targetLayer.equals("repository"),
                    "Presentation or mapping accesses persistence: " + owner.getName() + " -> " + target);
        if (layer.equals("service"))
            assertFalse(target.startsWith("jakarta.servlet.") || target.startsWith("org.springframework.web.servlet.")
                    || target.startsWith("org.springframework.web.bind.")
                    || target.equals("org.springframework.http.ResponseEntity"),
                    "Business service depends on HTTP presentation: " + owner.getName() + " -> " + target);
    }

    @Test
    void controllersKeepPersistenceAndServiceInternalsOutOfHttpContracts() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var bean : scanner.findCandidateComponents("com.example.backend")) {
            Class<?> controller = Class.forName(bean.getBeanClassName());
            if (controller.getName().contains("Test$")) continue;
            for (var field : controller.getDeclaredFields()) {
                assertFalse(Repository.class.isAssignableFrom(field.getType())
                        || EntityManager.class.isAssignableFrom(field.getType()), "Controller accesses persistence: " + field);
            }
            for (var handler : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(handler, RequestMapping.class) == null) continue;
                assertViewContract(handler.getGenericReturnType());
                for (var input : handler.getGenericParameterTypes()) assertViewContract(input);
            }
        }
    }

    private void assertViewContract(Type type) {
        assertViewContract(type, new HashSet<>());
    }

    private void assertViewContract(Type type, Set<Type> visited) {
        if (!visited.add(type)) return;
        if (type instanceof ParameterizedType generic) {
            assertViewContract(generic.getRawType(), visited);
            for (var argument : generic.getActualTypeArguments()) assertViewContract(argument, visited);
        } else if (type instanceof WildcardType wildcard) {
            for (var bound : wildcard.getUpperBounds()) assertViewContract(bound, visited);
            for (var bound : wildcard.getLowerBounds()) assertViewContract(bound, visited);
        } else if (type instanceof GenericArrayType array) {
            assertViewContract(array.getGenericComponentType(), visited);
        } else if (type instanceof Class<?> contract) {
            if (contract.isArray()) { assertViewContract(contract.getComponentType(), visited); return; }
            if (!contract.getName().startsWith(BASE)) return;
            assertFalse(contract.isAnnotationPresent(Entity.class), "Entity exposed over HTTP: " + contract.getName());
            assertTrue(Set.of("dto", "enums").contains(layerOf(contract.getName())),
                    "HTTP contract must be a DTO or enum: " + contract.getName());
            for (var field : contract.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers()))
                assertViewContract(field.getGenericType(), visited);
        }
    }

    @Test
    void exportApiCatalogWithRoleGates(WebApplicationContext context) throws Exception {
        var mvc = webAppContextSetup(context).apply(springSecurity()).build();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        var endpoints = new TreeMap<String, Map<String, Object>>();
        for (var bean : scanner.findCandidateComponents("com.example.backend")) {
            Class<?> controller = Class.forName(bean.getBeanClassName());
            if (controller.getName().contains("Test$")) continue;
            var base = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            for (var handler : controller.getDeclaredMethods()) {
                var mapping = AnnotatedElementUtils.findMergedAnnotation(handler, RequestMapping.class);
                if (mapping == null) continue;
                assertFalse(mapping.method().length == 0, "Specify HTTP methods: " + handler);
                for (String prefix : paths(base)) for (String suffix : paths(mapping)) {
                    String path = (prefix + "/" + suffix).replaceAll("/+", "/").replaceAll("/$", "");
                    for (var method : mapping.method()) {
                        String key = method + " " + path;
                        if (endpoints.containsKey(key)) continue; // JSON/multipart handlers share one API.
                        String probePath = path.replaceAll("\\{[^}]+}", "123");
                        List<String> roles = new ArrayList<>();
                        for (String role : List.of("GUEST", "ADMIN", "MANAGER", "REVIEWER", "SCHOOL", "STAFF", "STUDENT")) {
                            var probe = request(HttpMethod.valueOf(method.name()), probePath);
                            if (!role.equals("GUEST")) probe.with(user("catalog").roles(role));
                            int status = mvc.perform(probe).andReturn().getResponse().getStatus();
                            if (status == 200) roles.add(role);
                            else assertEquals(role.equals("GUEST") ? 401 : 403, status, key + " " + role);
                        }
                        endpoints.put(key, Map.of("method", method.name(), "path", path,
                                "controller", controller.getSimpleName(), "handler", handler.getName(), "roles", roles));
                    }
                }
            }
        }
        assertFalse(endpoints.isEmpty());
        Files.createDirectories(Path.of("target"));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of("target/api-catalog.json").toFile(),
                Map.of("total", endpoints.size(), "endpoints", endpoints.values()));
    }

    private String[] paths(RequestMapping mapping) {
        return mapping == null || mapping.path().length == 0 ? new String[]{""} : mapping.path();
    }
}
