package com.example.backend.schema.routing.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.service.problem.SchemaCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;

class SchemaRetrievalMetadataCatalogTest {
    @Test
    void everyMvpMetadataIdentityHasBothReviewedLocalesAndStableChecksum() {
        SchemaRetrievalMetadataCatalog catalog = new SchemaRetrievalMetadataCatalog(new ObjectMapper());

        assertEquals(29, catalog.size());
        var rc = catalog.find("circuits_rc_discharging", "1.11").orElseThrow();
        assertEquals("v2", SchemaRetrievalMetadataCatalog.PROJECTION_VERSION);
        assertEquals(2, rc.semanticViews().size());
        assertTrue(rc.semanticViews().stream().anyMatch(view -> view.locale().equals("vi")
                && view.text().contains("điện trở")));
        assertTrue(rc.semanticViews().stream().anyMatch(view -> view.locale().equals("en")
                && view.text().contains("resistor")));
        assertEquals(64, rc.metadataChecksum().length());
    }

    @Test
    void builderConsumesMetadataGenericallyWithoutSchemaIdRoutingBranches() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SchemaCompiler compiler = new SchemaCompiler(mapper);
        var definition = mapper.readTree("""
                {"version":"1.11","topic":"CIRCUITS","model":"rc_discharging",
                 "requiredQuantities":[
                   {"key":"voltage","aliases":["source_voltage"],"allowedUnits":["V"]},
                   {"key":"resistance","aliases":["r"],"allowedUnits":["ohm"]},
                   {"key":"capacitance","aliases":["c"],"allowedUnits":["F"]}],
                 "optionalQuantities":[],"adjustableParameters":[],
                 "execution":{"durationSeconds":1,"stepSeconds":0.1},
                 "validation":{"tolerance":0.01,"checkpointFractions":[1]},
                 "output":{"type":"timeseries"}}
                """);
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId("circuits_rc_discharging");
        schema.setVersion("1.11");
        schema.setTopic("CIRCUITS");
        schema.setName("RC discharging");
        schema.setDefinition(definition);
        schema.setDefinitionChecksum(compiler.checksum(definition));

        var document = new SchemaSearchDocumentBuilder(new SchemaRetrievalMetadataCatalog(mapper))
                .build(schema, compiler.compile(definition, schema.getSchemaId(), schema.getVersion(), schema.getTopic()));

        assertEquals("v2", document.projectionVersion());
        assertEquals(2, document.semanticViews().size());
        assertTrue(document.aliases().stream().anyMatch(alias -> alias.contains("tụ")));
        assertTrue(document.searchText().contains("capacitor"));
    }
}
