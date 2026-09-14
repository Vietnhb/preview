package com.example.backend.config;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DatabaseConfig {

    private static final int SUPABASE_TRANSACTION_POOLER_PORT = 6543;

    @Bean
    public DataSource dataSource(Environment environment) {
        String configuredUrl = firstNonBlank(
                environment.getProperty("SUPABASE_DB_URL"),
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("DIRECT_URL"));

        if (!StringUtils.hasText(configuredUrl)) {
            throw new IllegalStateException(
                    "Database URL is missing. Configure DATABASE_URL, DIRECT_URL, or SUPABASE_DB_URL.");
        }

        DatabaseConnection connection = DatabaseConnection.parse(
                configuredUrl,
                environment.getProperty("SUPABASE_DB_USERNAME"),
                environment.getProperty("SUPABASE_DB_PASSWORD"));

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("physlive-db-pool");
        hikari.setDriverClassName("org.postgresql.Driver");
        hikari.setJdbcUrl(connection.jdbcUrl());
        hikari.setUsername(connection.username());
        hikari.setPassword(connection.password());
        hikari.setMaximumPoolSize(10);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(15_000);
        return new HikariDataSource(hikari);
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private record DatabaseConnection(String jdbcUrl, String username, String password) {

        private static DatabaseConnection parse(String configuredUrl, String explicitUsername,
                String explicitPassword) {
            String normalizedUrl = stripOptionalQuotes(configuredUrl.trim());
            if (normalizedUrl.startsWith("jdbc:postgresql:")) {
                return new DatabaseConnection(
                        normalizedUrl,
                        requireCredential(explicitUsername, "Database username"),
                        requireCredential(explicitPassword, "Database password"));
            }

            int schemeSeparator = normalizedUrl.indexOf("://");
            if (schemeSeparator <= 0) {
                throw new IllegalStateException("Database URL does not contain a valid PostgreSQL scheme.");
            }
            String scheme = normalizedUrl.substring(0, schemeSeparator);
            if (!"postgresql".equalsIgnoreCase(scheme) && !"postgres".equalsIgnoreCase(scheme)) {
                throw new IllegalStateException("Database URL must use postgresql://, postgres://, or jdbc:postgresql://.");
            }

            ParsedPostgresUrl parsedUrl = parsePostgresUrl(normalizedUrl.substring(schemeSeparator + 3));
            Credentials embeddedCredentials = parseCredentials(parsedUrl.rawUserInfo());
            String username = StringUtils.hasText(explicitUsername) ? explicitUsername : embeddedCredentials.username();
            String password = StringUtils.hasText(explicitPassword) ? explicitPassword : embeddedCredentials.password();

            List<String> queryParameters = sanitizeQuery(parsedUrl.rawQuery());
            addIfMissing(queryParameters, "sslmode", "sslmode=require");
            if (parsedUrl.port() == SUPABASE_TRANSACTION_POOLER_PORT) {
                addIfMissing(queryParameters, "prepareThreshold", "prepareThreshold=0");
            }

            String host = parsedUrl.host().contains(":") ? "[" + parsedUrl.host() + "]" : parsedUrl.host();
            String query = queryParameters.isEmpty() ? "" : "?" + String.join("&", queryParameters);
            String jdbcUrl = "jdbc:postgresql://" + host + ":" + parsedUrl.port() + parsedUrl.path() + query;

            return new DatabaseConnection(
                    jdbcUrl,
                    requireCredential(username, "Database username"),
                    requireCredential(password, "Database password"));
        }

        private static ParsedPostgresUrl parsePostgresUrl(String urlWithoutScheme) {
            int querySeparator = urlWithoutScheme.indexOf('?');
            String rawQuery = querySeparator >= 0 ? urlWithoutScheme.substring(querySeparator + 1) : null;
            String withoutQuery = querySeparator >= 0 ? urlWithoutScheme.substring(0, querySeparator) : urlWithoutScheme;

            int pathSeparator = withoutQuery.indexOf('/');
            String authority = pathSeparator >= 0 ? withoutQuery.substring(0, pathSeparator) : withoutQuery;
            String path = pathSeparator >= 0 ? withoutQuery.substring(pathSeparator) : "/postgres";

            int credentialSeparator = authority.lastIndexOf('@');
            String rawUserInfo = credentialSeparator >= 0 ? authority.substring(0, credentialSeparator) : null;
            String hostAndPort = credentialSeparator >= 0 ? authority.substring(credentialSeparator + 1) : authority;
            HostAndPort hostAndPortParts = parseHostAndPort(hostAndPort);

            if (!StringUtils.hasText(hostAndPortParts.host())) {
                throw new IllegalStateException("Database URL does not contain a valid host.");
            }
            return new ParsedPostgresUrl(
                    rawUserInfo,
                    hostAndPortParts.host(),
                    hostAndPortParts.port(),
                    StringUtils.hasText(path) ? path : "/postgres",
                    rawQuery);
        }

        private static HostAndPort parseHostAndPort(String hostAndPort) {
            if (hostAndPort.startsWith("[")) {
                int closingBracket = hostAndPort.indexOf(']');
                if (closingBracket < 0) {
                    throw new IllegalStateException("Database URL contains an invalid IPv6 host.");
                }
                String host = hostAndPort.substring(1, closingBracket);
                int port = 5432;
                if (closingBracket + 1 < hostAndPort.length()) {
                    port = parsePort(hostAndPort.substring(closingBracket + 2));
                }
                return new HostAndPort(host, port);
            }

            int portSeparator = hostAndPort.lastIndexOf(':');
            if (portSeparator < 0) {
                return new HostAndPort(hostAndPort, 5432);
            }
            return new HostAndPort(
                    hostAndPort.substring(0, portSeparator),
                    parsePort(hostAndPort.substring(portSeparator + 1)));
        }

        private static int parsePort(String rawPort) {
            try {
                return Integer.parseInt(rawPort);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Database URL contains an invalid port.");
            }
        }

        private static Credentials parseCredentials(String rawUserInfo) {
            if (!StringUtils.hasText(rawUserInfo)) {
                return new Credentials(null, null);
            }
            int separator = rawUserInfo.indexOf(':');
            if (separator < 0) {
                return new Credentials(percentDecode(rawUserInfo), null);
            }
            String password = percentDecode(rawUserInfo.substring(separator + 1));
            if (password.length() >= 2 && password.startsWith("[") && password.endsWith("]")) {
                password = password.substring(1, password.length() - 1);
            }
            return new Credentials(percentDecode(rawUserInfo.substring(0, separator)), password);
        }

        private static List<String> sanitizeQuery(String rawQuery) {
            List<String> result = new ArrayList<>();
            if (!StringUtils.hasText(rawQuery)) {
                return result;
            }
            for (String parameter : rawQuery.split("&")) {
                String key = parameter.split("=", 2)[0];
                if (!key.equalsIgnoreCase("pgbouncer") && !key.equalsIgnoreCase("connection_limit")) {
                    result.add(parameter);
                }
            }
            return result;
        }

        private static void addIfMissing(List<String> parameters, String key, String value) {
            boolean present = parameters.stream()
                    .map(parameter -> parameter.split("=", 2)[0])
                    .anyMatch(existingKey -> existingKey.equalsIgnoreCase(key));
            if (!present) {
                parameters.add(value);
            }
        }

        private static String percentDecode(String value) {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        }

        private static String requireCredential(String value, String label) {
            if (!StringUtils.hasText(value)) {
                throw new IllegalStateException(label + " is missing from the database connection configuration.");
            }
            return value;
        }

        private static String stripOptionalQuotes(String value) {
            if (value.length() >= 2) {
                char first = value.charAt(0);
                char last = value.charAt(value.length() - 1);
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    return value.substring(1, value.length() - 1).trim();
                }
            }
            return value;
        }

        private record Credentials(String username, String password) {
        }

        private record ParsedPostgresUrl(String rawUserInfo, String host, int port, String path, String rawQuery) {
        }

        private record HostAndPort(String host, int port) {
        }
    }
}
