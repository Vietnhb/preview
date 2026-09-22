package com.example.backend.simulation.assets;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/** The same catalog file is imported by the frontend; IDs name exact bundled SVGs. */
@Component
public final class SvgAssetCatalog {
    private final List<Asset> entries;
    private final Map<String, Asset> byId;
    private final String checksum;

    public SvgAssetCatalog(ObjectMapper mapper, ResourceLoader resources) {
        try (var stream = resources.getResource("classpath:assets/svg-catalog.json").getInputStream()) {
            byte[] bytes = stream.readAllBytes();
            var document = mapper.readTree(bytes);
            if (!document.path("version").canConvertToInt() || document.path("version").asInt() < 1
                    || !document.path("assets").isArray() || document.path("assets").isEmpty()) {
                throw new IllegalArgumentException("Invalid SVG catalog version or assets");
            }
            entries = List.of(mapper.treeToValue(document.path("assets"), Asset[].class));
            var index = new LinkedHashMap<String, Asset>();
            for (Asset asset : entries) {
                if (index.putIfAbsent(asset.id(), asset) != null) {
                    throw new IllegalArgumentException("Duplicate SVG asset ID: " + asset.id());
                }
            }
            byId = Map.copyOf(index);
            checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot load the shared SVG asset catalog", failure);
        }
    }

    public List<Asset> entries() {
        return entries;
    }

    public Asset require(String id) {
        Asset asset = byId.get(id);
        if (asset == null) throw new IllegalArgumentException("Unknown SVG asset ID: " + id);
        return asset;
    }

    public String checksum() {
        return checksum;
    }

    public record Asset(String id, String family, String kind, String label, String description,
            List<String> tags, List<String> aliases, String file, List<Double> viewBox,
            String anchor, List<String> effects) {
        public Asset {
            if (id == null || id.isBlank() || family == null || family.isBlank()
                    || label == null || label.isBlank() || description == null || description.isBlank()
                    || file == null || !file.endsWith(".svg")
                    || !("actor".equals(kind) || "prop".equals(kind))) {
                throw new IllegalArgumentException("Invalid SVG asset metadata: " + id);
            }
            tags = tags == null ? List.of() : List.copyOf(tags);
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            effects = effects == null ? List.of() : List.copyOf(effects);
            viewBox = List.copyOf(viewBox);
            if (viewBox.size() != 4 || viewBox.stream().anyMatch(value -> !Double.isFinite(value))
                    || viewBox.get(2) <= 0 || viewBox.get(3) <= 0) {
                throw new IllegalArgumentException("Invalid SVG viewBox: " + id);
            }
        }
    }
}
