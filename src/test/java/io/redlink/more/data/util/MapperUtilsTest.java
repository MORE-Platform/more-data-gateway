package io.redlink.more.data.util;

import io.redlink.more.data.exception.BadRequestException;
import io.redlink.more.data.model.Alias;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapperUtilsTest {

    @Test
    @DisplayName("writeValueAsString serializes map to json")
    void writeValueAsString_serializes() {
        String json = MapperUtils.writeValueAsString(Map.of("a", 1, "b", "x"));
        assertThat(json).isIn("{\"a\":1,\"b\":\"x\"}", "{\"b\":\"x\",\"a\":1}");
    }

    @Test
    @DisplayName("readValue returns null for null input")
    void readValue_null_returnsNull() {
        Map<?, ?> value = MapperUtils.readValue(null, Map.class);
        assertThat(value).isNull();
    }

    @Test
    @DisplayName("readValue throws BadRequestException for invalid json")
    void readValue_invalid_throws() {
        assertThatThrownBy(() -> MapperUtils.readValue("not-json", Map.class))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("convertValueWithAliases copies value to replacement key if missing")
    void convertValueWithAliases_copiesWhenMissing() {
        Map<String, Object> src = Map.of("oldKey", 42);
        Alias alias = new Alias("oldKey", "newKey");

        Map out = MapperUtils.convertValueWithAliases(src, Map.class, List.of(alias));

        assertThat(out).containsEntry("oldKey", 42)
                .containsEntry("newKey", 42);
    }

    @Test
    @DisplayName("convertValueWithAliases does not overwrite existing replacement key")
    void convertValueWithAliases_doesNotOverwrite() {
        Map<String, Object> src = Map.of("oldKey", 42, "newKey", 99);
        Alias alias = new Alias("oldKey", "newKey");

        Map out = MapperUtils.convertValueWithAliases(src, Map.class, List.of(alias));

        assertThat(out).containsEntry("newKey", 99)
                .containsEntry("oldKey", 42);
    }

    @Test
    @DisplayName("isPrimitiveLike correctly identifies primitive-like values")
    void isPrimitiveLike_checks() {
        assertThat(MapperUtils.isPrimitiveLike("str")).isTrue();
        assertThat(MapperUtils.isPrimitiveLike(123)).isTrue();
        assertThat(MapperUtils.isPrimitiveLike(123.45)).isTrue();
        assertThat(MapperUtils.isPrimitiveLike(true)).isTrue();
        assertThat(MapperUtils.isPrimitiveLike('c')).isTrue();
        assertThat(MapperUtils.isPrimitiveLike(Thread.State.NEW)).isTrue();

        assertThat(MapperUtils.isPrimitiveLike(Map.of("a", 1))).isFalse();
        assertThat(MapperUtils.isPrimitiveLike(List.of(1, 2, 3))).isFalse();
        assertThat(MapperUtils.isPrimitiveLike(new Object())).isFalse();
    }
}
