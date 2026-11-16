package com.fathzer.sync4j.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fathzer.sync4j.Folder;

class SynchronizationConfigTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static SynchronizationConfig parse(String json) throws IOException {
        return SynchronizationConfig.parse(MAPPER.readTree(json), ProviderRegistry.withDefaults());
    }

    private static String library(String id, String src, String dest) {
        return "{\"id\":\"" + id + "\",\"source\":{\"provider\":\"local\",\"path\":\"" + src + "\"},\"destination\":{\"provider\":\"local\",\"path\":\"" + dest + "\"}}";
    }

    private static String libraries(String... libraries) {
        return "{\"libraries\":[" + String.join(",", libraries) + "]}";
    }

    @BeforeEach
    void setUp() {
        TestProviderFactory.LAST_CONFIG.set(null);
    }

    @Test
    void testParse() throws IOException {
        final SynchronizationConfig config = parse("{\"libraries\":[{\"id\":\"lib-1\",\"label\":\"Library 1\"," +
                "\"source\":{\"provider\":\"local\",\"path\":\"/tmp\"},\"destination\":{\"provider\":\"local\",\"path\":\"/dst\"}}],\"filters\":[\"- *.tmp\",\"- *.db\"]}");

        assertEquals(List.of("- *.tmp", "- *.db"), config.filters());
        assertEquals(1, config.libraries().size());
        final Library library = config.libraries().get(0);
        assertEquals("lib-1", library.id());
        assertEquals("Library 1", library.label());
        assertEquals("/tmp", library.source().path());
        assertEquals("/dst", library.destination().path());
        // The provider is usable
        final Folder opened = library.source().entry(true);
        assertTrue(opened.isFolder());
        opened.getFileProvider().close();
    }

    @Test
    void testLibraryFilters() throws IOException {
        final String loc = "\"source\":{\"provider\":\"local\",\"path\":\"/s\"},\"destination\":{\"provider\":\"local\",\"path\":\"/d\"}";
        final SynchronizationConfig config = parse("{\"filters\":[\"- *.tmp\",\"- *.trucs\"],\"libraries\":[" +
                "{\"id\":\"a\"," + loc + ",\"filters\":[\"+ *.trucs\",\"- *.db\"]}," +
                "{\"id\":\"b\"," + loc + "}]}");
        final Library a = config.libraries().get(0);
        final Library b = config.libraries().get(1);
        assertEquals(List.of("+ *.trucs", "- *.db"), a.filters());
        assertTrue(b.filters().isEmpty());
        // The rules of a library are tried before the global ones
        assertEquals(List.of("+ *.trucs", "- *.db", "- *.tmp", "- *.trucs"), config.filters(a));
        assertEquals(List.of("- *.tmp", "- *.trucs"), config.filters(b));
        // The global ones are not modified
        assertEquals(List.of("- *.tmp", "- *.trucs"), config.filters());
    }

    @Test
    void testInvalidFilters() {
        final String loc = "\"source\":{\"provider\":\"local\",\"path\":\"/s\"},\"destination\":{\"provider\":\"local\",\"path\":\"/d\"}";
        assertThrows(IOException.class, () -> parse("{\"filters\":[\"- *.{tmp\"]}"), "Invalid global pattern");
        assertThrows(IOException.class, () -> parse("{\"filters\":[\"*.tmp\"]}"), "A rule needs a sign");
        assertThrows(IOException.class, () -> parse("{\"libraries\":[{\"id\":\"a\"," + loc + ",\"filters\":[\"-\"]}]}"), "Invalid rule of a library");
        assertDoesNotThrow(() -> parse("{\"filters\":[\"+ /keep/**\",\"- *.tmp\"]}"));
        // The old name is refused, not ignored
        final IOException e = assertThrows(IOException.class, () -> parse("{\"excludePatterns\":[\"*.tmp\"]}"));
        assertTrue(e.getMessage().contains("filters"), e.getMessage());
        assertThrows(IOException.class, () -> parse("{\"libraries\":[{\"id\":\"a\"," + loc + ",\"excludePatterns\":[\"*.tmp\"]}]}"));
    }

    @Test
    void testDefaults() throws IOException {
        final SynchronizationConfig config = parse(libraries(library("a", "/src", "/dst")));

        assertTrue(config.filters().isEmpty());
        // The label is the id by default
        assertEquals("a", config.libraries().get(0).label());
        // No library is valid
        assertTrue(parse("{}").libraries().isEmpty());
    }

    @Test
    void testInvalidConfigurations() {
        assertThrows(IOException.class, () -> SynchronizationConfig.parse(null, ProviderRegistry.withDefaults()), "The description is required");
        assertThrows(IOException.class, () -> parse("[]"), "The description should be an object");
        assertThrows(IOException.class, () -> parse("{\"libraries\":[{\"label\":\"no id\"}]}"));
        assertThrows(IOException.class, () -> parse("{\"libraries\":[{\"id\":\"a\",\"destination\":{\"provider\":\"local\",\"path\":\"/d\"}}]}"), "source is required");
        assertThrows(IOException.class, () -> parse("{\"libraries\":[{\"id\":\"a\",\"source\":{\"path\":\"/s\"},\"destination\":{\"provider\":\"local\",\"path\":\"/d\"}}]}"), "provider is required");
        assertThrows(IOException.class, () -> parse("{\"libraries\":[{\"id\":\"a\",\"source\":{\"provider\":\"local\"},\"destination\":{\"provider\":\"local\",\"path\":\"/d\"}}]}"), "path is required");
        assertThrows(IOException.class, () -> parse(libraries(library("bad id", "/s", "/d"))));
        final IOException unknown = assertThrows(IOException.class, () -> parse("{\"libraries\":[{\"id\":\"a\"," +
                "\"source\":{\"provider\":\"ftp\",\"path\":\"/s\"},\"destination\":{\"provider\":\"local\",\"path\":\"/d\"}}]}"));
        assertEquals("Unknown provider: ftp", unknown.getMessage());
        final IOException twice = assertThrows(IOException.class, () -> parse(libraries(library("a", "/s", "/d"), library("a", "/s2", "/d2"))));
        assertTrue(twice.getMessage().contains("defined twice"));
    }

    @Test
    void testUndefinedEnvironmentVariable() {
        final IOException inPath = assertThrows(IOException.class, () -> parse(libraries(library("a", "${SYNC4J_CONFIG_SURELY_UNDEFINED}/x", "/d"))));
        assertTrue(inPath.getMessage().contains("SYNC4J_CONFIG_SURELY_UNDEFINED"), inPath.getMessage());

        // The variables of the configuration of a provider are also resolved, whatever the provider
        final IOException inConfig = assertThrows(IOException.class, () -> parse("{\"libraries\":[{\"id\":\"a\"," +
                "\"source\":{\"provider\":\"test-memory\",\"path\":\"/s\",\"config\":{\"text\":\"${SYNC4J_CONFIG_SURELY_UNDEFINED}\"}},\"destination\":{\"provider\":\"local\",\"path\":\"/d\"}}]}"));
        assertTrue(inConfig.getMessage().contains("SYNC4J_CONFIG_SURELY_UNDEFINED"), inConfig.getMessage());
        assertNull(TestProviderFactory.LAST_CONFIG.get(), "The factory should not be called with an invalid configuration");
    }

    private static String testLocation(String config) {
        return "{\"libraries\":[{\"id\":\"a\",\"source\":{\"provider\":\"test-memory\",\"path\":\"/s\"" + (config == null ? "" : ",\"config\":" + config)
                + "},\"destination\":{\"provider\":\"local\",\"path\":\"/d\"}}]}";
    }

    @Test
    void testProviderConfiguration() throws IOException {
        // PATH is defined on every system
        parse(testLocation("{\"text\":\"${PATH}\",\"number\":42,\"decimal\":1.5,\"flag\":true,\"mode\":\"fast\",\"list\":[\"x\",\"${PATH}\"],\"nested\":{\"key\":\"${PATH}\"}}"));

        final TestProviderFactory.TestConfig config = TestProviderFactory.LAST_CONFIG.get();
        assertEquals(System.getenv("PATH"), config.text());
        assertEquals(42, config.number());
        assertEquals(1.5, config.decimal());
        assertTrue(config.flag());
        // The names of the enumeration values are not case sensitive
        assertEquals(TestProviderFactory.Mode.FAST, config.mode());
        assertEquals(List.of("x", System.getenv("PATH")), config.list());
        assertEquals(Map.of("key", System.getenv("PATH")), config.nested());
    }

    @Test
    void testTextsAreConvertedToTheTypeOfTheProperties() throws IOException {
        // It is what happens with an environment variable, that is always a text
        parse(testLocation("{\"text\":\"t\",\"number\":\"42\",\"flag\":\"true\"}"));

        final TestProviderFactory.TestConfig config = TestProviderFactory.LAST_CONFIG.get();
        assertEquals(42, config.number());
        assertTrue(config.flag());
    }

    @Test
    void testNoConfiguration() throws IOException {
        // The factory receives a configuration whose properties are not set, and has to check the ones it needs
        parse(testLocation(null));

        assertEquals(new TestProviderFactory.TestConfig(null, 0, 0, false, null, null, null), TestProviderFactory.LAST_CONFIG.get());
        TestProviderFactory.LAST_CONFIG.set(null);
        parse(testLocation("null"));
        assertNotNull(TestProviderFactory.LAST_CONFIG.get());
        TestProviderFactory.LAST_CONFIG.set(null);
        parse(testLocation("{}"));
        assertNotNull(TestProviderFactory.LAST_CONFIG.get());
    }

    @Test
    void testInvalidProviderConfigurations() {
        // The messages tell what is wrong, and where
        final IOException unknown = assertThrows(IOException.class, () -> parse(testLocation("{\"txt\":\"x\"}")));
        assertTrue(unknown.getMessage().contains("unknown property 'txt'"), unknown.getMessage());
        assertTrue(unknown.getMessage().contains("text"), "The known properties should be listed: " + unknown.getMessage());
        assertTrue(unknown.getMessage().contains("source of library a"), "The location should be named: " + unknown.getMessage());

        final IOException badEnum = assertThrows(IOException.class, () -> parse(testLocation("{\"mode\":\"turbo\"}")));
        assertTrue(badEnum.getMessage().contains("property mode"), badEnum.getMessage());
        assertTrue(badEnum.getMessage().contains("FAST"), "The accepted values should be listed: " + badEnum.getMessage());

        final IOException badNumber = assertThrows(IOException.class, () -> parse(testLocation("{\"number\":\"many\"}")));
        assertTrue(badNumber.getMessage().contains("property number"), badNumber.getMessage());

        final IOException badNesting = assertThrows(IOException.class, () -> parse(testLocation("{\"nested\":{\"a\":{\"b\":1}}}")));
        assertTrue(badNesting.getMessage().contains("property nested.a"), badNesting.getMessage());

        assertThrows(IOException.class, () -> parse(testLocation("\"text\"")), "The configuration should be an object");
        assertThrows(IOException.class, () -> parse(testLocation("[]")));
        // The factory validates the values it receives
        assertEquals("The text is invalid", assertThrows(IOException.class, () -> parse(testLocation("{\"text\":\"invalid\"}"))).getMessage());
    }

    @Test
    void testProvidersWithoutConfiguration() throws IOException {
        // An empty configuration is the same as no configuration
        assertEquals(1, parse("{\"libraries\":[{\"id\":\"a\",\"source\":{\"provider\":\"local\",\"path\":\"/s\",\"config\":{}},\"destination\":{\"provider\":\"local\",\"path\":\"/d\"}}]}").libraries().size());

        final IOException e = assertThrows(IOException.class, () -> parse("{\"libraries\":[{\"id\":\"a\",\"source\":{\"provider\":\"local\",\"path\":\"/s\",\"config\":{\"x\":1}}," +
                "\"destination\":{\"provider\":\"local\",\"path\":\"/d\"}}]}"));
        assertTrue(e.getMessage().contains("local provider has no configuration"), e.getMessage());
    }

    @Test
    void testPathIsOptionalForProvidersWithoutRootPath() throws IOException {
        // The memory provider has no root path
        final SynchronizationConfig config = parse("{\"libraries\":[{\"id\":\"a\",\"source\":{\"provider\":\"memory\"},\"destination\":{\"provider\":\"local\",\"path\":\"/d\"}}]}");

        final FolderLocation source = config.libraries().get(0).source();
        assertEquals("", source.path());
        final Folder folder = source.entry(true);
        assertTrue(folder.isFolder());
        folder.getFileProvider().close();
        // ... but it can't have a sub folder
        assertThrows(IOException.class, () -> source.withSuffix("sub").entry(true));
        // The path is still required for the other providers
        assertThrows(IOException.class, () -> parse("{\"libraries\":[{\"id\":\"a\",\"source\":{\"provider\":\"memory\"},\"destination\":{\"provider\":\"local\"}}]}"));
    }

    @Test
    void testCustomRegistry() throws IOException {
        final JsonNode json = MAPPER.readTree(libraries(library("a", "/s", "/d")));
        // The providers that are not registered can't be used
        assertThrows(IOException.class, () -> SynchronizationConfig.parse(json, new ProviderRegistry()));
        assertEquals(1, SynchronizationConfig.parse(json, new ProviderRegistry().register(new com.fathzer.sync4j.file.LocalProviderFactory())).libraries().size());
    }
}
