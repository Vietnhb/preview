package com.example.backend.simulation.assets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import com.fasterxml.jackson.databind.ObjectMapper;

class SvgAssetCatalogTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void bundledCatalogResolvesExactVariantIdsAndHasStableChecksum() {
        var catalog = new SvgAssetCatalog(mapper, new DefaultResourceLoader());
        assertFalse(catalog.entries().isEmpty());
        for (var asset : catalog.entries()) assertEquals(asset, catalog.require(asset.id()));
        assertEquals(64, catalog.checksum().length());
        assertEquals(catalog.checksum(), new SvgAssetCatalog(mapper, new DefaultResourceLoader()).checksum());
        assertThrows(IllegalArgumentException.class, () -> catalog.require("a car of any color"));
    }

    @Test
    void refusesDuplicateAssetIds() throws Exception {
        var catalog = mapper.createObjectNode().put("version", 1);
        var asset = mapper.readTree("""
                {"id":"block","family":"block","kind":"actor","label":"Block","description":"A blue block",
                 "tags":[],"aliases":[],"file":"block.svg","viewBox":[0,0,64,64],"anchor":"center","effects":[]}
                """);
        catalog.putArray("assets").add(asset).add(asset);
        var resources = mock(ResourceLoader.class);
        when(resources.getResource("classpath:assets/svg-catalog.json"))
                .thenReturn(new ByteArrayResource(catalog.toString().getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> new SvgAssetCatalog(mapper, resources));
    }
}
