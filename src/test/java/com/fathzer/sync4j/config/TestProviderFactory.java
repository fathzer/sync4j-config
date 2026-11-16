package com.fathzer.sync4j.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fathzer.sync4j.FileProvider;
import com.fathzer.sync4j.FileProviderFactory;
import com.fathzer.sync4j.memory.MemoryFileProvider;
import com.fathzer.sync4j.util.IOLambda.IOFunction;

/**
 * A factory declared in the META-INF/services of the tests: it records the configuration it receives.
 */
public class TestProviderFactory implements FileProviderFactory<TestProviderFactory.TestConfig> {
    public enum Mode {
        FAST, SLOW
    }

    /** A configuration with the kinds of values that a provider can need. */
    public record TestConfig(String text, int number, double decimal, boolean flag, Mode mode, List<String> list, Map<String, String> nested) {}

    static final AtomicReference<TestConfig> LAST_CONFIG = new AtomicReference<>();

    @Override
    public String type() {
        return "test-memory";
    }

    @Override
    public Class<TestConfig> configType() {
        return TestConfig.class;
    }

    @Override
    public IOFunction<String, FileProvider> create(TestConfig config) throws IOException {
        if ("invalid".equals(config.text())) {
            throw new IOException("The text is invalid");
        }
        LAST_CONFIG.set(config);
        return root -> new MemoryFileProvider();
    }
}
