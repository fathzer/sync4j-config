package com.fathzer.sync4j.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;



import com.fathzer.sync4j.FileProvider;
import com.fathzer.sync4j.util.IOLambda.IOFunction;

class LibraryTest {
    private static final IOFunction<String, FileProvider> NULL_PROVIDER = r -> null;
    private static final String SOURCE_PATH = "/source";
    private static final String DEST_PATH = "/destination";
    private static final String TEST_LABEL = "Test Library";
    
    private static final FolderLocation TEST_SOURCE = new FolderLocation(NULL_PROVIDER, SOURCE_PATH);
    private static final FolderLocation TEST_DESTINATION = new FolderLocation(NULL_PROVIDER, DEST_PATH);

    @Test
    void testLibraryConstructor_ValidId() {
        // Test with valid alphanumeric id
        assertDoesNotThrow(() -> new Library("test123", TEST_LABEL, TEST_SOURCE, TEST_DESTINATION));
        // Test with hyphen and underscore
        assertDoesNotThrow(() -> new Library("test-123", TEST_LABEL, TEST_SOURCE, TEST_DESTINATION));
        assertDoesNotThrow(() -> new Library("test_123", TEST_LABEL, TEST_SOURCE, TEST_DESTINATION));
        assertDoesNotThrow(() -> new Library("test-1_2-3", TEST_LABEL, TEST_SOURCE, TEST_DESTINATION));

        assertThrows(NullPointerException.class, () -> new Library(null, TEST_LABEL, TEST_SOURCE, TEST_DESTINATION));
        assertThrows(IllegalArgumentException.class, () -> new Library("", TEST_LABEL, TEST_SOURCE, TEST_DESTINATION));
        assertThrows(IllegalArgumentException.class, () -> new Library("   ", TEST_LABEL, TEST_SOURCE, TEST_DESTINATION));
    }

    @Test
    void testLibraryConstructor_NonAlphanumericId() {
        // Test with various invalid characters
        String[] invalidIds = {"test@123", "test.123", "test#123", "test 123", "test/123", "test\\123"};
        for (String id : invalidIds) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> new Library(id, TEST_LABEL, TEST_SOURCE, TEST_DESTINATION)
            );
            assertEquals("Library id must contain only alphanumeric characters, hyphens, or underscores", 
                exception.getMessage(), 
                "Failed for id: " + id);
        }
    }
}
