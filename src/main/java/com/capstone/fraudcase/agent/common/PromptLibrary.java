package com.capstone.fraudcase.agent.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads prompt resources from classpath:prompts and renders {{variable}} placeholders. Both AI adapters send the
 * exact same rendered strings, which keeps the prompt contract identical across frameworks.
 */
@Component
public class PromptLibrary {

    public static final String SYSTEM = "fraud-system.txt";
    public static final String USER = "fraud-user.txt";
    public static final String SCHEMA = "output-schema.json";
    public static final String FOLLOW_UP = "follow-up-system.txt";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String raw(String name) {
        return cache.computeIfAbsent(name, n -> {
            try (InputStream in = new ClassPathResource("prompts/" + n).getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Missing prompt resource " + n, e);
            }
        });
    }

    public String render(String name, Map<String, String> variables) {
        String text = raw(name);
        for (Map.Entry<String, String> v : variables.entrySet()) {
            text = text.replace("{{" + v.getKey() + "}}", v.getValue() == null ? "" : v.getValue());
        }
        return text;
    }
}
