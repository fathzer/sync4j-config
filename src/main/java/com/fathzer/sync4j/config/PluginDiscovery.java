package com.fathzer.sync4j.config;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fathzer.plugin.loader.classloader.ClassLoaderPluginLoader;
import com.fathzer.plugin.loader.jar.JarPluginLoader;
import com.fathzer.plugin.loader.utils.FileUtils;

/**
 * Finds the implementations of an extension point (for instance, the {@link com.fathzer.sync4j.FileProviderFactory} of the providers).
 * <p>
 * They are searched:
 * </p>
 * <ul>
 * <li>on the classpath of the application</li>
 * <li>in the jar files of a directory (optional). It allows to add a plugin or a provider to an application without rebuilding it.</li>
 * </ul>
 * <p>
 * An implementation is declared, as for {@link java.util.ServiceLoader}, in the <code>META-INF/services/&lt;full name of the interface&gt;</code> file of its jar,
 * and must have a public constructor without argument.
 * <br>An implementation that can't be loaded is logged and ignored: it does not prevent the other ones to be discovered.
 * </p>
 */
public final class PluginDiscovery {
    private static final Logger log = LoggerFactory.getLogger(PluginDiscovery.class);

    private PluginDiscovery() {
        // Utility class
    }

    /**
     * Discovers the implementations of an interface.
     * @param type the interface (the implementations need a public constructor without argument)
     * @param jarDirectory a directory that contains jar files (null, or a directory that does not exist, to search the classpath only)
     * @param <T> the type of the implementations
     * @return the implementations, the ones of the classpath first. The jars of the directory are read in alphabetical order.
     */
    public static <T> List<T> discover(Class<T> type, Path jarDirectory) {
        final List<T> result = new ArrayList<>();
        final ClassLoader appLoader = PluginDiscovery.class.getClassLoader();
        try {
            result.addAll(new ClassLoaderPluginLoader()
                    .withExceptionConsumer(e -> log.warn("A {} of the classpath can't be loaded", type.getSimpleName(), e))
                    .getPlugins(appLoader, type));
        } catch (IOException e) {
            log.warn("The classpath can't be searched for {}", type.getSimpleName(), e);
        }
        if (jarDirectory != null && Files.isDirectory(jarDirectory)) {
            try {
                final List<Path> jars = FileUtils.getJarFiles(jarDirectory, 1);
                jars.sort(Comparator.naturalOrder());
                for (Path jar : jars) {
                    result.addAll(discover(type, jar, appLoader));
                }
            } catch (IOException e) {
                log.warn("The directory {} can't be read", jarDirectory, e);
            }
        } else if (jarDirectory != null) {
            log.debug("The plugin directory {} does not exist", jarDirectory);
        }
        return result;
    }

    private static <T> List<T> discover(Class<T> type, Path jar, ClassLoader parent) {
        try {
            // The classes of the jar can use the ones of the application (the interface that they implement, for instance)
            final JarPluginLoader loader = new JarPluginLoader() {
                @Override
                protected ClassLoader buildClassLoader(Path jarFile) {
                    return new URLClassLoader(new URL[] {FileUtils.getURL(jarFile)}, parent);
                }
            };
            loader.withExceptionConsumer(e -> log.warn("A {} of {} can't be loaded", type.getSimpleName(), jar, e));
            return loader.getPlugins(jar, type);
        } catch (IOException | RuntimeException e) {
            log.warn("{} can't be searched for {}", jar, type.getSimpleName(), e);
            return List.of();
        }
    }
}
