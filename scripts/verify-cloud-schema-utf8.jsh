import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.net.*;

var properties = new Properties();
try (var reader = Files.newBufferedReader(Path.of(".env.local"))) { properties.load(reader); }
var configured = properties.getProperty("DATABASE_URL").trim();
if (configured.length() >= 2 && ((configured.startsWith("\"") && configured.endsWith("\""))
        || (configured.startsWith("'") && configured.endsWith("'")))) {
    configured = configured.substring(1, configured.length() - 1);
}
var rest = configured.substring(configured.indexOf("://") + 3);
var queryIndex = rest.indexOf('?');
var beforeQuery = queryIndex >= 0 ? rest.substring(0, queryIndex) : rest;
var slash = beforeQuery.indexOf('/');
var authority = slash >= 0 ? beforeQuery.substring(0, slash) : beforeQuery;
var databasePath = slash >= 0 ? beforeQuery.substring(slash) : "/postgres";
var at = authority.lastIndexOf('@');
var userInfo = at >= 0 ? authority.substring(0, at) : "";
var hostPort = at >= 0 ? authority.substring(at + 1) : authority;
var colon = userInfo.indexOf(':');
var username = URLDecoder.decode(colon >= 0 ? userInfo.substring(0, colon) : userInfo, java.nio.charset.StandardCharsets.UTF_8);
var password = URLDecoder.decode(colon >= 0 ? userInfo.substring(colon + 1) : "", java.nio.charset.StandardCharsets.UTF_8);
if (password.length() >= 2 && password.startsWith("[") && password.endsWith("]")) password = password.substring(1, password.length() - 1);
var portSeparator = hostPort.lastIndexOf(':');
var host = portSeparator >= 0 ? hostPort.substring(0, portSeparator) : hostPort;
var port = portSeparator >= 0 ? hostPort.substring(portSeparator + 1) : "5432";
var jdbcUrl = "jdbc:postgresql://" + host + ":" + port + databasePath + "?sslmode=require&prepareThreshold=0";

try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
     var statement = connection.createStatement()) {
    try (var result = statement.executeQuery("SELECT lifecycle_status, count(*) FROM schema_versions GROUP BY lifecycle_status ORDER BY lifecycle_status")) {
        while (result.next()) System.out.println("status=" + result.getString(1) + " count=" + result.getLong(2));
    }
    try (var result = statement.executeQuery("SELECT count(*) FROM schema_versions WHERE definition_checksum IS NULL")) {
        result.next(); System.out.println("null_definition_checksums=" + result.getLong(1));
    }
    for (var table : List.of("schema_search_embeddings", "schema_search_index_generations", "schema_search_embedding_views")) {
        try (var result = statement.executeQuery("SELECT to_regclass('" + table + "')")) {
            result.next(); System.out.println(table + "_present=" + (result.getString(1) != null));
        }
    }
    try (var result = statement.executeQuery("SELECT count(*) FROM schema_versions WHERE definition::text LIKE '%Ã%' OR definition::text LIKE '%Â%' OR definition::text LIKE '%Ä%' OR definition::text LIKE '%Ï%' OR definition::text LIKE '%Î%' OR definition::text LIKE '%â‚%' OR definition::text LIKE '%Ì%' OR definition::text LIKE '%á»%' OR definition::text LIKE '%áº%'")) {
        result.next(); System.out.println("definitions_with_mojibake_markers=" + result.getLong(1));
    }
    try (var result = statement.executeQuery("SELECT count(*) FROM schema_versions WHERE NOT (definition ? 'model')")) {
        result.next(); System.out.println("definitions_missing_model=" + result.getLong(1));
    }
    try (var result = statement.executeQuery("SELECT schema_id || '@' || version FROM schema_versions WHERE definition::text LIKE '%' || chr(195) || '%' OR definition::text LIKE '%' || chr(194) || '%' OR definition::text LIKE '%' || chr(239) || chr(191) || chr(189) || '%' OR definition::text LIKE '%' || chr(65533) || '%' ORDER BY schema_id, version")) {
        while (result.next()) System.out.println("mojibake_identity=" + result.getString(1));
    }
    try (var result = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success = true AND version = '17'")) {
        result.next(); System.out.println("flyway_v17_success_rows=" + result.getLong(1));
    }
    try (var result = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success = true AND version = '18'")) {
        result.next(); System.out.println("flyway_v18_success_rows=" + result.getLong(1));
    }
    try (var result = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success = true AND version = '19'")) {
        result.next(); System.out.println("flyway_v19_success_rows=" + result.getLong(1));
    }
    try (var result = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success = true AND version = '20'")) {
        result.next(); System.out.println("flyway_v20_success_rows=" + result.getLong(1));
    }
}
System.out.println("cloud_utf8_readback=ok");
