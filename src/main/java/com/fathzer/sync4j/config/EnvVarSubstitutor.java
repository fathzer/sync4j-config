package com.fathzer.sync4j.config;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class for substituting environment variable placeholders in strings.
 * Placeholders should be in the format ${ENV_VAR_NAME}.
 */
public class EnvVarSubstitutor {
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private final Map<String, String> environmentVars;

    /**
     * Creates a new EnvVarSubstitutor that uses the system environment variables.
     */
    public EnvVarSubstitutor() {
        this(System.getenv());
    }

    /**
     * Creates a new EnvVarSubstitutor with custom environment variables.
     * @param environmentVars A map of environment variable names to their values.
     */
    public EnvVarSubstitutor(Map<String, String> environmentVars) {
        this.environmentVars = environmentVars;
    }

    /**
     * Substitutes environment variables in the input string.
     * @param input The input string containing placeholders like ${ENV_VAR_NAME}
     * @return A new string with all placeholders replaced by their corresponding environment variable values.
     *         If an environment variable is not found, the placeholder remains unchanged.
     * @throws NullPointerException if the input string is null
     */
    public String substitute(String input) {
        Objects.requireNonNull(input);

        StringBuilder result = new StringBuilder();
        Matcher matcher = ENV_VAR_PATTERN.matcher(input);
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = environmentVars.getOrDefault(varName, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }

    /**
     * A convenience static method to substitute environment variables in a string using system environment variables.
     * @param input The input string containing placeholders
     * @return A new string with environment variables substituted
     * @throws NullPointerException if the input string is null
     */
    public static String replace(String input) {
        return new EnvVarSubstitutor().substitute(input);
    }
    
    /**
     * Substitutes environment variables in a string of a configuration, using the system environment variables.
     * @param input The input string containing placeholders like ${ENV_VAR_NAME}
     * @param wording What the string is, used in the error message (for instance "token of the pCloud provider")
     * @return The input in which the placeholders are replaced by the value of the environment variables
     * @throws IOException if one of the variables is not defined
     */
    public static String replaceStrict(String input, String wording) throws IOException {
        final EnvVarSubstitutor substitutor = new EnvVarSubstitutor();
        final Set<String> undefinedVars = substitutor.findUndefinedVars(input);
        if (!undefinedVars.isEmpty()) {
            throw new IOException("Undefined environment variables in " + wording + " definition (" + input + "): " + String.join(", ", undefinedVars));
        }
        return substitutor.substitute(input);
    }

    /**
     * Finds all undefined environment variables in the input string.
     * @param input The input string containing placeholders like ${ENV_VAR_NAME}
     * @return A Set of environment variable names that are referenced in the input but not defined.
     *         Returns an empty set if all variables are defined or if the input is null.
     * @throws NullPointerException if the input string is null
     */
    public Set<String> findUndefinedVars(String input) {
        Objects.requireNonNull(input);
        
        Set<String> undefinedVars = new HashSet<>();
        Matcher matcher = ENV_VAR_PATTERN.matcher(input);
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!environmentVars.containsKey(varName)) {
                undefinedVars.add(varName);
            }
        }
        
        return undefinedVars;
    }
}
