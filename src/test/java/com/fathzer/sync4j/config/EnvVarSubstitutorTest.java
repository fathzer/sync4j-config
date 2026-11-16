package com.fathzer.sync4j.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnvVarSubstitutorTest {
    private Map<String, String> testEnvVars;
    private EnvVarSubstitutor substitutor;

    @BeforeEach
    void setUp() {
        testEnvVars = new HashMap<>();
        testEnvVars.put("TEST_VAR", "testValue");
        testEnvVars.put("ANOTHER_VAR", "123");
        testEnvVars.put("ESCAPE_ME", "special$characters\\with");
        substitutor = new EnvVarSubstitutor(testEnvVars);
    }

    @Test
    void testSubstituteSingleVariable() {
        String result = substitutor.substitute("This is a ${TEST_VAR} test");
        assertEquals("This is a testValue test", result);
    }

    @Test
    void testSubstituteMultipleVariables() {
        String result = substitutor.substitute("${TEST_VAR} and ${ANOTHER_VAR}");
        assertEquals("testValue and 123", result);
    }

    @Test
    void testSubstituteWithSpecialCharacters() {
        String result = substitutor.substitute("Special: ${ESCAPE_ME}");
        assertEquals("Special: special$characters\\with", result);
    }

    @Test
    void testSubstituteNonExistentVariable() {
        String result = substitutor.substitute("Unknown: ${NON_EXISTENT}");
        assertEquals("Unknown: ${NON_EXISTENT}", result);
    }

    @Test
    void testSubstituteEmptyString() {
        String result = substitutor.substitute("");
        assertEquals("", result);
    }

    @Test
    void testSubstituteNullInput() {
        assertThrows(NullPointerException.class, () -> substitutor.substitute(null));
    }

    @Test
    void testSubstituteWithCustomEnvironment() {
        Map<String, String> customVars = new HashMap<>();
        customVars.put("CUSTOM", "customValue");
        EnvVarSubstitutor customSubstitutor = new EnvVarSubstitutor(customVars);
        
        String result = customSubstitutor.substitute("${CUSTOM} and ${TEST_VAR}");
        assertEquals("customValue and ${TEST_VAR}", result);
    }

    @Test
    void testStaticMethodWithSpecificValue() {
        // Given: A string with a variable that's guaranteed to exist
        String testKey = "TEST_STATIC_VAR";
        String testValue = "testStaticValue";
        
        // When: Using the static method to substitute the variable
        String result = EnvVarSubstitutor.replace("Test: ${" + testKey + "}");
        
        // Then: The result should either contain the environment variable's value
        // or leave the placeholder if the variable doesn't exist
        if (System.getenv(testKey) != null) {
            assertEquals("Test: " + testValue, result);
        } else {
            assertEquals("Test: ${" + testKey + "}", result);
        }
    }

    @Test
    void testMultipleOccurrences() {
        String result = substitutor.substitute("${TEST_VAR} and ${TEST_VAR} again ${TEST_VAR}");
        assertEquals("testValue and testValue again testValue", result);
    }

    @Test
    void testUnclosedVariable() {
        String result = substitutor.substitute("Unclosed: ${TEST_VAR");
        assertEquals("Unclosed: ${TEST_VAR", result);
    }
    
    @Test
    void testFindUndefinedVarsNoneUndefined() {
        Set<String> undefined = substitutor.findUndefinedVars("${TEST_VAR} and ${ANOTHER_VAR}");
        assertTrue(undefined.isEmpty());
    }
    
    @Test
    void testFindUndefinedVarsSingleUndefined() {
        Set<String> undefined = substitutor.findUndefinedVars("${TEST_VAR} and ${UNDEFINED_VAR}");
        assertEquals(1, undefined.size());
        assertTrue(undefined.contains("UNDEFINED_VAR"));
    }
    
    @Test
    void testFindUndefinedVarsMultipleUndefined() {
        Set<String> undefined = substitutor.findUndefinedVars(
            "${TEST_VAR}, ${UNDEFINED_1}, ${ANOTHER_VAR}, ${UNDEFINED_2}");
        assertEquals(2, undefined.size());
        assertTrue(undefined.contains("UNDEFINED_1"));
        assertTrue(undefined.contains("UNDEFINED_2"));
    }
    
    @Test
    void testFindUndefinedVarsNullInput() {
        assertThrows(NullPointerException.class, () -> substitutor.findUndefinedVars(null));
    }
    
    @Test
    void testFindUndefinedVarsNoPlaceholders() {
        Set<String> undefined = substitutor.findUndefinedVars("No placeholders here");
        assertTrue(undefined.isEmpty());
    }
    
    @Test
    void testFindUndefinedVarsWithEmptyString() {
        Set<String> undefined = substitutor.findUndefinedVars("");
        assertTrue(undefined.isEmpty());
    }
}
