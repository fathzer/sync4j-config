package com.fathzer.sync4j.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import com.fathzer.sync4j.FileProvider;

/**
 * A library: a source folder that can be synchronized with a destination folder.
 * @param id the id of the library (it can contain alphanumeric characters, hyphens and underscores)
 * @param label the label displayed to the user
 * @param source the source
 * @param destination the destination
 */
public record Library(String id, String label, FolderLocation source, FolderLocation destination) {
    public Library {
        if (id.trim().isEmpty()) {
            throw new IllegalArgumentException("Library id cannot be empty");
        }
        if (!id.matches("^[a-zA-Z0-9-_]+$")) {
            throw new IllegalArgumentException("Library id must contain only alphanumeric characters, hyphens, or underscores");
        }
        Objects.requireNonNull(source);
        Objects.requireNonNull(destination);
        label = label == null ? id : label;
    }

    /**
     * Checks that the source and destination can be opened (the destination is created if it does not exist).
     * @return this
     * @throws UncheckedIOException if one of the folders can't be opened
     */
    public Library check() {
        checkFolder(source, true);
        checkFolder(destination, false);
        return this;
    }

    private static void checkFolder(FolderLocation location, boolean isSource) {
        try (FileProvider provider = location.entry(isSource).getFileProvider()) {
            // Nothing to do, the folder could be opened
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
