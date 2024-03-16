package dev.blaauwendraad.masker.json;

import dev.blaauwendraad.masker.json.config.JsonMaskingConfig;
import dev.blaauwendraad.masker.json.config.KeyMaskingConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class CustomKeyMaskingConfigTest {

    @Test
    void maskingWithDefaultConfig() {
        JsonMasker jsonMasker = JsonMasker.getMasker(JsonMaskingConfig.builder()
                .maskKeys(Set.of("string", "number", "boolean"))
                .maskJsonPaths(Set.of("$.stringPath", "$.numberPath", "$.booleanPath"))
                .build());

        String masked = jsonMasker.mask("""
                {
                  "string": "maskMe",
                  "stringPath": "maskMe",
                  "number": 12345,
                  "numberPath": 12345,
                  "boolean": false,
                  "booleanPath": false
                }
                """);
        Assertions.assertThat(masked).isEqualTo("""
                {
                  "string": "***",
                  "stringPath": "***",
                  "number": "###",
                  "numberPath": "###",
                  "boolean": "&&&",
                  "booleanPath": "&&&"
                }
                """
        );
    }

    @Test
    void maskingWithCustomConfig() {
        JsonMasker jsonMasker = JsonMasker.getMasker(JsonMaskingConfig.builder()
                .maskKeys(Set.of("string", "number", "boolean"))
                .maskJsonPaths(Set.of("$.stringPath", "$.numberPath", "$.booleanPath"))
                .maskStringsWith("(string)")
                .maskNumbersWith("(number)")
                .maskBooleansWith("(boolean)")
                .build());

        String masked = jsonMasker.mask("""
                {
                  "string": "maskMe",
                  "stringPath": "maskMe",
                  "number": 12345,
                  "numberPath": 12345,
                  "boolean": false,
                  "booleanPath": false
                }
                """);
        Assertions.assertThat(masked).isEqualTo("""
                {
                  "string": "(string)",
                  "stringPath": "(string)",
                  "number": "(number)",
                  "numberPath": "(number)",
                  "boolean": "(boolean)",
                  "booleanPath": "(boolean)"
                }
                """
        );
    }

    @Test
    void maskingWithCustomConfigForTheKey() {
        JsonMasker jsonMasker = JsonMasker.getMasker(JsonMaskingConfig.builder()
                .maskKeys(Set.of("string", "number", "boolean"))
                .maskJsonPaths(Set.of("$.stringPath", "$.numberPath", "$.booleanPath"))
                .maskKeys(Set.of("stringCustom", "numberCustom", "booleanCustom"), KeyMaskingConfig.builder()
                        .maskStringsWith("(string)")
                        .maskNumbersWith("(number)")
                        .maskBooleansWith("(boolean)")
                        .build()
                )
                .maskJsonPaths(Set.of("$.stringPathCustom", "$.numberPathCustom", "$.booleanPathCustom"), KeyMaskingConfig.builder()
                        .maskStringsWith("(string)")
                        .maskNumbersWith("(number)")
                        .maskBooleansWith("(boolean)")
                        .build()
                )
                .build());

        String masked = jsonMasker.mask("""
                {
                  "string": "maskMe",
                  "stringPath": "maskMe",
                  "stringCustom": "maskMe",
                  "stringPathCustom": "maskMe",
                  "number": 12345,
                  "numberPath": 12345,
                  "numberCustom": 12345,
                  "numberPathCustom": 12345,
                  "boolean": false,
                  "booleanPath": false,
                  "booleanCustom": false,
                  "booleanPathCustom": false
                }
                """);
        Assertions.assertThat(masked).isEqualTo("""
                {
                  "string": "***",
                  "stringPath": "***",
                  "stringCustom": "(string)",
                  "stringPathCustom": "(string)",
                  "number": "###",
                  "numberPath": "###",
                  "numberCustom": "(number)",
                  "numberPathCustom": "(number)",
                  "boolean": "&&&",
                  "booleanPath": "&&&",
                  "booleanCustom": "(boolean)",
                  "booleanPathCustom": "(boolean)"
                }
                """
        );
    }

    @Test
    void maskingConfigsForConfigsShouldBeInherited() {
        JsonMasker jsonMasker = JsonMasker.getMasker(JsonMaskingConfig.builder()
                .maskKeys(Set.of("string", "number", "boolean", "a"))
                .maskKeys(Set.of("b"), KeyMaskingConfig.builder()
                        .maskStringsWith("(mask_b.string)")
                        .maskNumbersWith("(mask_b.number)")
                        .maskBooleansWith("(mask_b.boolean)")
                        .build()
                )
                .maskKeys(Set.of("c"), KeyMaskingConfig.builder()
                        .maskStringsWith("(mask_c.string)")
                        .maskNumbersWith("(mask_c.number)")
                        .maskBooleansWith("(mask_c.boolean)")
                        .build()
                )
                .build());

        String masked = jsonMasker.mask("""
                {
                  "a": {
                    "string": "maskMe",
                    "number": 12345,
                    "boolean": false,
                    "b": {
                      "string": "maskMe",
                      "number": 12345,
                      "boolean": false,
                      "c": {
                        "string": "maskMe",
                        "number": 12345,
                        "boolean": false
                      },
                      "d": {
                        "string": "maskMe",
                        "number": 12345,
                        "boolean": false
                      }
                    },
                    "e": {
                      "string": "maskMe",
                      "number": 12345,
                      "boolean": false
                    }
                  }
                }
                """);

        Assertions.assertThat(masked).isEqualTo("""
                {
                  "a": {
                    "string": "***",
                    "number": "###",
                    "boolean": "&&&",
                    "b": {
                      "string": "(mask_b.string)",
                      "number": "(mask_b.number)",
                      "boolean": "(mask_b.boolean)",
                      "c": {
                        "string": "(mask_c.string)",
                        "number": "(mask_c.number)",
                        "boolean": "(mask_c.boolean)"
                      },
                      "d": {
                        "string": "(mask_b.string)",
                        "number": "(mask_b.number)",
                        "boolean": "(mask_b.boolean)"
                      }
                    },
                    "e": {
                      "string": "***",
                      "number": "###",
                      "boolean": "&&&"
                    }
                  }
                }
                """);
    }

    @Test
    void maskEmailKeepPrefixAndDomain() {
        JsonMasker jsonMasker = JsonMasker.getMasker(JsonMaskingConfig.builder()
                .maskKeys(Set.of("string", "number", "boolean"))
                .maskKeys(Set.of("email"), KeyMaskingConfig.builder()
                        .maskStringsWith(ValueMasker.maskEmail(2, true, "***"))
                        .build()
                )
                .build());

        String masked = jsonMasker.mask("""
                {
                  "string": "maskMe",
                  "number": 12345,
                  "boolean": false,
                  "email": "agavlyukovskiy@gmail.com"
                }
                """);
        Assertions.assertThat(masked).isEqualTo("""
                {
                  "string": "***",
                  "number": "###",
                  "boolean": "&&&",
                  "email": "ag***@gmail.com"
                }
                """
        );
    }

    @Test
    void maskEmailKeepPrefix() {
        JsonMasker jsonMasker = JsonMasker.getMasker(JsonMaskingConfig.builder()
                .maskKeys(Set.of("string", "number", "boolean"))
                .maskKeys(Set.of("email"), KeyMaskingConfig.builder()
                        .maskStringsWith(ValueMasker.maskEmail(3, false, "***"))
                        .build()
                )
                .build());

        String masked = jsonMasker.mask("""
                {
                  "string": "maskMe",
                  "number": 12345,
                  "boolean": false,
                  "email": "agavlyukovskiy@gmail.com"
                }
                """);
        Assertions.assertThat(masked).isEqualTo("""
                {
                  "string": "***",
                  "number": "###",
                  "boolean": "&&&",
                  "email": "aga***"
                }
                """
        );
    }

    @Test
    void maskWithStringFunction() {
        JsonMasker jsonMasker = JsonMasker.getMasker(JsonMaskingConfig.builder()
                .maskKeys(Set.of("string", "number", "boolean"))
                .maskKeys(Set.of("function"), KeyMaskingConfig.builder()
                        .maskStringsWith(ValueMasker.maskWithStringFunction(value -> value.replaceAll("\\[this secret]", "***")))
                        .build()
                )
                .maskKeys(Set.of("functionNull"), KeyMaskingConfig.builder()
                        .maskStringsWith(ValueMasker.maskWithStringFunction(value -> null))
                        .build()
                )
                .maskKeys(Set.of("functionConditional"), KeyMaskingConfig.builder()
                        .maskStringsWith(ValueMasker.maskWithStringFunction(value -> value.startsWith("secret:") ? "***" : value))
                        .build()
                )
                .build());

        String masked = jsonMasker.mask("""
                {
                  "string": "maskMe",
                  "number": 12345,
                  "boolean": false,
                  "function": "mask [this secret] please",
                  "functionNull": "maskMe",
                  "functionConditional": {
                    "value1": "not a secret",
                    "value2": "secret: very much"
                  }
                }
                """);
        Assertions.assertThat(masked).isEqualTo("""
                {
                  "string": "***",
                  "number": "###",
                  "boolean": "&&&",
                  "function": "mask *** please",
                  "functionNull": null,
                  "functionConditional": {
                    "value1": "not a secret",
                    "value2": "***"
                  }
                }
                """
        );
    }
}
