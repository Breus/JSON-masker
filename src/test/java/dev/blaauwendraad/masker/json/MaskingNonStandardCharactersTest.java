package dev.blaauwendraad.masker.json;

import dev.blaauwendraad.masker.json.config.JsonMaskingConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class MaskingNonStandardCharactersTest {

    @Test
    void maskingNonStandardCharacters() {
        JsonMasker jsonMasker = JsonMasker.getMasker(Set.of("привет", "💩"));

        Assertions.assertEquals(
                """
                        {
                          "привет": "*****",
                          "otherKey": null,
                          "💩": "************",
                          "someObject": {
                            "привет": "*****",
                            "otherKey": null,
                            "💩": {
                                "💩": "************"
                            }
                          },
                          "someArray": [
                            "💩",
                            "💩".
                            {
                              "привет": "*****",
                              "otherKey": null,
                              "💩": {
                                  "💩": "************"
                              }
                            }
                          ]
                        }
                        """,
                jsonMasker.mask(
                        """
                                {
                                  "привет": "hello",
                                  "otherKey": null,
                                  "💩": "shit happens",
                                  "someObject": {
                                    "привет": "hello",
                                    "otherKey": null,
                                    "💩": {
                                        "💩": "shit happens"
                                    }
                                  },
                                  "someArray": [
                                    "💩",
                                    "💩".
                                    {
                                      "привет": "hello",
                                      "otherKey": null,
                                      "💩": {
                                          "💩": "shit happens"
                                      }
                                    }
                                  ]
                                }
                                """
                )
        );
    }

    @Test
    void maskingNonStandardCharactersInAllowMode() {
        JsonMasker jsonMasker = JsonMasker.getMasker(
                JsonMaskingConfig.custom(Set.of("привет", "otherKey", "someArray"), JsonMaskingConfig.TargetKeyMode.ALLOW).build()
        );

        Assertions.assertEquals(
                """
                        {
                          "привет": "hello",
                          "otherKey": null,
                          "💩": "************",
                          "someObject": {
                            "привет": "hello",
                            "otherKey": null,
                            "💩": {
                                "💩": "************"
                            }
                          },
                          "someArray": [
                            "💩",
                            "💩".
                            {
                              "привет": "hello",
                              "otherKey": null,
                              "💩": {
                                  "💩": "shit happens"
                              }
                            }
                          ]
                        }
                        """,
                jsonMasker.mask(
                        """
                                {
                                  "привет": "hello",
                                  "otherKey": null,
                                  "💩": "shit happens",
                                  "someObject": {
                                    "привет": "hello",
                                    "otherKey": null,
                                    "💩": {
                                        "💩": "shit happens"
                                    }
                                  },
                                  "someArray": [
                                    "💩",
                                    "💩".
                                    {
                                      "привет": "hello",
                                      "otherKey": null,
                                      "💩": {
                                          "💩": "shit happens"
                                      }
                                    }
                                  ]
                                }
                                """
                )
        );
    }
}
