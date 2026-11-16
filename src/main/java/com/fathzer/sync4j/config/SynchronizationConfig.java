package com.fathzer.sync4j.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fathzer.sync4j.FileProvider;
import com.fathzer.sync4j.FileProviderFactory;
import com.fathzer.sync4j.util.FilterRules;
import com.fathzer.sync4j.util.IOLambda.IOFunction;

/**
 * What to synchronize: the libraries and what is not synchronized.
 * <p>
 * It is read from the JSON description of the synchronization (see {@link #parse(JsonNode, ProviderRegistry)}):
 * </p>
 * <pre>
 * {
 *   "libraries": [
 *     {
 *       "id": "photos",
 *       "label": "My photos",
 *       "source": { "provider": "pCloud", "path": "/Photos", "config": { "zone": "us", "token": "${PCLOUD_TOKEN}" } },
 *       "destination": { "provider": "local", "path": "${LOCAL_PATH_PREFIX}/photos" },
 *       "filters": ["- *.xmp"]
 *     }
 *   ],
 *   "filters": ["- *.tmp"]
 * }
 * </pre>
 * <p>
 * Environment variables (<code>${VARIABLE}</code>) can be used in the <code>path</code> of a location and in the text values of the <code>config</code> of its provider.
 * It is an error if a variable is not defined. The <code>config</code> is converted to the configuration object of the provider (see {@link FileProviderFactory#configType()}).
 * The <code>path</code> is optional only for the providers that have no root path (see {@link FileProviderFactory#isRootPathRequired()}).
 * </p>
 * @param libraries the libraries
 * @param filters the filter rules (see {@link FilterRules}) that apply to all the libraries. A library can have its own ones, that are tried first (see {@link Library#filters()} and {@link #filters(Library)})
 */
public record SynchronizationConfig(List<Library> libraries, List<String> filters) {
    public SynchronizationConfig {
        Objects.requireNonNull(libraries);
        Objects.requireNonNull(filters);
    }

    /**
     * Reads the JSON description of a synchronization.
     * @param node the JSON description
     * @param providers the available providers
     * @return the configuration
     * @throws IOException if the description is invalid (a missing attribute, an unknown provider, an invalid configuration of a provider, an undefined environment variable, ...)
     */
    public static SynchronizationConfig parse(JsonNode node, ProviderRegistry providers) throws IOException {
        Objects.requireNonNull(providers);
        if (node == null || !node.isObject()) {
            throw new IOException("The synchronization configuration is missing");
        }
        final List<Library> libraries = new ArrayList<>();
        final Set<String> ids = new HashSet<>();
        for (JsonNode libraryNode : node.path("libraries")) {
            final Library library = parseLibrary(libraryNode, providers);
            if (!ids.add(library.id())) {
                throw new IOException("Library id " + library.id() + " is defined twice");
            }
            libraries.add(library);
        }
        return new SynchronizationConfig(libraries, rules(node, "the synchronization"));
    }

    private static List<String> rules(JsonNode node, String wording) throws IOException {
        if (node.has("excludePatterns")) {
            throw new IOException("excludePatterns of " + wording + " is not supported anymore, it was replaced by filters (rules like \"- *.tmp\", see the documentation)");
        }
        final List<String> rules = new ArrayList<>();
        node.path("filters").forEach(n -> rules.add(n.asText()));
        try {
            FilterRules.of(rules); // Fails fast if a rule is invalid
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid filters of " + wording + ": " + e.getMessage(), e);
        }
        return rules;
    }

    /**
     * Gets the filter rules to apply to a library.
     * @param library a library of this configuration
     * @return The rules of the library followed by the global ones (the first rule that matches an entry decides, so a library can override the global rules)
     */
    public List<String> filters(Library library) {
        final List<String> all = new ArrayList<>(library.filters());
        all.addAll(filters);
        return all;
    }

    private static Library parseLibrary(JsonNode node, ProviderRegistry providers) throws IOException {
        final String id = text(node, "id", "library id");
        final String label = node.has("label") ? node.get("label").asText() : id;
        try {
            return new Library(id, label, parseLocation(node.get("source"), providers, "source of library " + id), parseLocation(node.get("destination"), providers, "destination of library " + id), rules(node, "library " + id));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid library " + id + ": " + e.getMessage(), e);
        }
    }

    private static FolderLocation parseLocation(JsonNode node, ProviderRegistry providers, String wording) throws IOException {
        if (node == null) {
            throw new IOException("Missing " + wording);
        }
        final String type = text(node, "provider", "provider of " + wording);
        final FileProviderFactory<?> factory = providers.get(type);
        final String path;
        if (node.hasNonNull("path")) {
            path = EnvVarSubstitutor.replaceStrict(node.get("path").asText(), "path of " + wording);
        } else if (factory.isRootPathRequired()) {
            throw new IOException("Missing path of " + wording);
        } else {
            path = "";
        }
        return new FolderLocation(create(factory, node.get("config"), "configuration of " + wording), path);
    }

    /** Builds the configuration of the provider and creates the function that opens it. */
    private static <T> IOFunction<String, FileProvider> create(FileProviderFactory<T> factory, JsonNode config, String wording) throws IOException {
        return factory.create(ProviderConfigBinder.bind(factory, config, wording));
    }

    private static String text(JsonNode node, String field, String wording) throws IOException {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IOException("Missing " + wording);
        }
        return value.asText();
    }
}
