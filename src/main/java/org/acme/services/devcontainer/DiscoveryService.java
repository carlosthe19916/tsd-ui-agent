package org.acme.services.devcontainer;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class DiscoveryService {

    private static final int MAX_DEPTH = 5;
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", "out",
            "vendor", ".gradle", ".mvn", "__pycache__", ".venv", "venv",
            ".idea", ".vscode", ".settings", "bin", "obj");

    @Inject
    FeatureCatalog catalog;

    public DiscoveryResult discover(Path worktreePath) {
        Set<String> languages = new LinkedHashSet<>();
        Set<String> tools = new LinkedHashSet<>();
        Map<String, String> versions = new LinkedHashMap<>();

        try {
            detectManifests(worktreePath, languages);
            scanFileExtensions(worktreePath, languages);
            detectToolIndicators(worktreePath, tools);
            inferVersions(worktreePath, languages, versions);
        } catch (Exception e) {
            Log.warnf(e, "Discovery partially failed for %s, proceeding with what was found", worktreePath);
        }

        Log.infof("Discovery result: languages=%s, tools=%s, versions=%s", languages, tools, versions);

        return new DiscoveryResult(
                new ArrayList<>(languages),
                new ArrayList<>(tools),
                versions);
    }

    private void detectManifests(Path root, Set<String> languages) {
        Map<String, List<String>> allManifests = catalog.getAllLanguageManifests();
        for (Map.Entry<String, List<String>> entry : allManifests.entrySet()) {
            for (String manifest : entry.getValue()) {
                if (Files.exists(root.resolve(manifest))) {
                    languages.add(entry.getKey());
                    break;
                }
            }
        }
    }

    private void scanFileExtensions(Path root, Set<String> languages) throws IOException {
        Files.walkFileTree(root, Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName().toString();
                if (SKIP_DIRS.contains(dirName) && !dir.equals(root)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                int dot = fileName.lastIndexOf('.');
                if (dot > 0) {
                    String ext = fileName.substring(dot).toLowerCase();
                    String lang = catalog.languageForExtension(ext);
                    if (lang != null) {
                        languages.add(lang);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void detectToolIndicators(Path root, Set<String> tools) {
        Map<String, List<String>> toolIndicators = catalog.getToolIndicators();
        for (Map.Entry<String, List<String>> entry : toolIndicators.entrySet()) {
            for (String indicator : entry.getValue()) {
                if (Files.exists(root.resolve(indicator))) {
                    tools.add(entry.getKey());
                    break;
                }
            }
        }
    }

    private void inferVersions(Path root, Set<String> languages, Map<String, String> versions) {
        for (String language : languages) {
            String version = switch (language) {
                case "java" -> inferJavaVersion(root);
                case "node" -> inferNodeVersion(root);
                case "python" -> inferPythonVersion(root);
                case "go" -> inferGoVersion(root);
                case "rust" -> inferRustVersion(root);
                case "ruby" -> inferRubyVersion(root);
                case "php" -> inferPhpVersion(root);
                case "dotnet" -> inferDotnetVersion(root);
                default -> null;
            };
            if (version != null) {
                versions.put(language, version);
            }
        }
    }

    private String inferJavaVersion(Path root) {
        // pom.xml: maven.compiler.release or maven.compiler.source
        Path pom = root.resolve("pom.xml");
        if (Files.exists(pom)) {
            String content = readFileQuietly(pom);
            if (content != null) {
                String version = extractXmlProperty(content, "maven.compiler.release");
                if (version == null) {
                    version = extractXmlProperty(content, "maven.compiler.source");
                }
                if (version == null) {
                    version = extractXmlProperty(content, "java.version");
                }
                if (version != null) return normalizeVersion(version, true);
            }
        }

        // build.gradle or build.gradle.kts: sourceCompatibility
        for (String gradleFile : List.of("build.gradle", "build.gradle.kts")) {
            Path gradle = root.resolve(gradleFile);
            if (Files.exists(gradle)) {
                String content = readFileQuietly(gradle);
                if (content != null) {
                    Matcher m = Pattern.compile("sourceCompatibility\\s*=\\s*['\"]?([\\d.]+)['\"]?")
                            .matcher(content);
                    if (m.find()) return normalizeVersion(m.group(1), true);

                    m = Pattern.compile("JavaLanguageVersion\\.of\\((\\d+)\\)").matcher(content);
                    if (m.find()) return m.group(1);
                }
            }
        }

        // .java-version
        String version = readSingleLineFile(root.resolve(".java-version"));
        if (version != null) return normalizeVersion(version, true);

        // .sdkmanrc
        Path sdkmanrc = root.resolve(".sdkmanrc");
        if (Files.exists(sdkmanrc)) {
            String content = readFileQuietly(sdkmanrc);
            if (content != null) {
                Matcher m = Pattern.compile("java=([\\d.]+)").matcher(content);
                if (m.find()) return normalizeVersion(m.group(1), true);
            }
        }

        return null;
    }

    private String inferNodeVersion(Path root) {
        // package.json: engines.node
        Path packageJson = root.resolve("package.json");
        if (Files.exists(packageJson)) {
            String content = readFileQuietly(packageJson);
            if (content == null) return null;
            try {
                JsonObject pkg = Json.createReader(new StringReader(content)).readObject();
                if (pkg.containsKey("engines")) {
                    JsonObject engines = pkg.getJsonObject("engines");
                    if (engines.containsKey("node")) {
                        return normalizeVersion(engines.getString("node"), false);
                    }
                }
            } catch (Exception e) {
                Log.debugf("Failed to parse package.json for node version: %s", e.getMessage());
            }
        }

        // .nvmrc
        String version = readSingleLineFile(root.resolve(".nvmrc"));
        if (version != null) return normalizeVersion(version, false);

        // .node-version
        version = readSingleLineFile(root.resolve(".node-version"));
        if (version != null) return normalizeVersion(version, false);

        return null;
    }

    private String inferPythonVersion(Path root) {
        // pyproject.toml: requires-python
        Path pyproject = root.resolve("pyproject.toml");
        if (Files.exists(pyproject)) {
            String content = readFileQuietly(pyproject);
            if (content != null) {
                Matcher m = Pattern.compile("requires-python\\s*=\\s*\"([^\"]+)\"").matcher(content);
                if (m.find()) return normalizeVersion(m.group(1), false);
            }
        }

        // .python-version
        String version = readSingleLineFile(root.resolve(".python-version"));
        if (version != null) return normalizeVersion(version, false);

        return null;
    }

    private String inferGoVersion(Path root) {
        Path goMod = root.resolve("go.mod");
        if (Files.exists(goMod)) {
            String content = readFileQuietly(goMod);
            if (content != null) {
                Matcher m = Pattern.compile("^go\\s+(\\d+\\.\\d+)", Pattern.MULTILINE).matcher(content);
                if (m.find()) return m.group(1);
            }
        }
        return null;
    }

    private String inferRustVersion(Path root) {
        // rust-toolchain.toml
        Path toolchain = root.resolve("rust-toolchain.toml");
        if (Files.exists(toolchain)) {
            String content = readFileQuietly(toolchain);
            if (content != null) {
                Matcher m = Pattern.compile("channel\\s*=\\s*\"([^\"]+)\"").matcher(content);
                if (m.find()) return m.group(1);
            }
        }

        // rust-toolchain (plain file)
        String version = readSingleLineFile(root.resolve("rust-toolchain"));
        if (version != null) return version.trim();

        return null;
    }

    private String inferRubyVersion(Path root) {
        // .ruby-version
        String version = readSingleLineFile(root.resolve(".ruby-version"));
        if (version != null) return normalizeVersion(version, false);

        // Gemfile: ruby directive
        Path gemfile = root.resolve("Gemfile");
        if (Files.exists(gemfile)) {
            String content = readFileQuietly(gemfile);
            if (content != null) {
                Matcher m = Pattern.compile("ruby\\s+['\"]([\\d.]+)['\"]").matcher(content);
                if (m.find()) return m.group(1);
            }
        }

        return null;
    }

    private String inferPhpVersion(Path root) {
        Path composer = root.resolve("composer.json");
        if (Files.exists(composer)) {
            String content = readFileQuietly(composer);
            if (content == null) return null;
            try {
                JsonObject pkg = Json.createReader(new StringReader(content)).readObject();
                if (pkg.containsKey("require")) {
                    JsonObject require = pkg.getJsonObject("require");
                    if (require.containsKey("php")) {
                        return normalizeVersion(require.getString("php"), false);
                    }
                }
            } catch (Exception e) {
                Log.debugf("Failed to parse composer.json for php version: %s", e.getMessage());
            }
        }
        return null;
    }

    private String inferDotnetVersion(Path root) {
        // Look for *.csproj with TargetFramework
        try (var stream = Files.list(root)) {
            var csproj = stream.filter(p -> p.toString().endsWith(".csproj")).findFirst();
            if (csproj.isPresent()) {
                String content = readFileQuietly(csproj.get());
                if (content != null) {
                    Matcher m = Pattern.compile("<TargetFramework>net(\\d+\\.\\d+)</TargetFramework>")
                            .matcher(content);
                    if (m.find()) return m.group(1);
                }
            }
        } catch (IOException e) {
            Log.debugf("Failed to scan for csproj files: %s", e.getMessage());
        }
        return null;
    }

    private String extractXmlProperty(String xml, String propertyName) {
        Pattern p = Pattern.compile("<" + Pattern.quote(propertyName) + ">\\s*([^<]+?)\\s*</"
                + Pattern.quote(propertyName) + ">");
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    static String normalizeVersion(String raw, boolean majorOnly) {
        if (raw == null || raw.isBlank()) return null;
        // Strip common prefixes
        String v = raw.strip().replaceFirst("^[v^~>=<]*\\s*", "");
        // Remove trailing wildcards like .x or .*
        v = v.replaceAll("\\.[xX*]+$", "");
        if (v.isEmpty()) return null;

        if (majorOnly) {
            // Take just the major version number
            Matcher m = Pattern.compile("(\\d+)").matcher(v);
            return m.find() ? m.group(1) : null;
        }
        // Take major.minor if available, otherwise just major
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(v);
        return m.find() ? m.group(1) : null;
    }

    private String readFileQuietly(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            Log.debugf("Failed to read %s: %s", path, e.getMessage());
            return null;
        }
    }

    private String readSingleLineFile(Path path) {
        if (!Files.exists(path)) return null;
        String content = readFileQuietly(path);
        if (content == null || content.isBlank()) return null;
        return content.lines().findFirst().map(String::strip).filter(s -> !s.isEmpty()).orElse(null);
    }
}
