package io.redlink.more.data.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.redlink.more.data.exception.BadRequestException;
import io.redlink.more.data.model.Alias;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapperUtils {
    public static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public static String writeValueAsString(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    public static <T> T readValue(Object o, Class<T> c) {
        if (o == null) return null;
        try {
            return MAPPER.readValue(o.toString(), c);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    public static <T> T convertValue(Object o, Class<T> c) {
        return MAPPER.convertValue(o, c);
    }

    public static <T> T convertValueWithAliases(Object o, Class<T> targetClass, List<Alias> aliases) {
        if (o == null) return null;

        Map<String, Object> sourceMap = convertValue(o, Map.class);
        Map<String, Object> mappedData = new HashMap<>(sourceMap);

        aliases.forEach((alias) -> {
            if (!mappedData.containsKey(alias.replacement()) && mappedData.containsKey(alias.key())) {
                mappedData.put(alias.replacement(), mappedData.get(alias.key()));
            }
        });

        return convertValue(mappedData, targetClass);
    }

    public static boolean isPrimitiveLike(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>;
    }

    public static String getParameter(Map<String, String> parameters, String... keys) {
        for (String key : keys) {
            if (parameters.containsKey(key)) {
                return parameters.get(key);
            }
        }
        for (String key : keys) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    public static boolean containsParameter(Map<String, String> parameters, String... keys) {
        return Arrays.stream(keys).anyMatch(parameters::containsKey);
    }
}
