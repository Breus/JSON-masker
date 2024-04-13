package dev.blaauwendraad.masker.json;

import dev.blaauwendraad.masker.json.config.JsonMaskingConfig;
import dev.blaauwendraad.masker.json.config.KeyMaskingConfig;
import dev.blaauwendraad.masker.json.util.AsciiCharacter;
import dev.blaauwendraad.masker.json.util.AsciiJsonUtil;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of the {@link JsonMasker}.
 */
final class KeyContainsMasker implements JsonMasker {
    /**
     * Look-up trie containing the target keys.
     */
    private final KeyMatcher keyMatcher;
    /**
     * The masking configuration for the JSON masking process.
     */
    private final JsonMaskingConfig maskingConfig;

    /**
     * Creates an instance of an {@link KeyContainsMasker}
     *
     * @param maskingConfig the {@link JsonMaskingConfig} for the created masker
     */
    KeyContainsMasker(JsonMaskingConfig maskingConfig) {
        this.maskingConfig = maskingConfig;
        this.keyMatcher = new KeyMatcher(maskingConfig);
    }

    /**
     * Masks the values in the given input for all values having keys corresponding to any of the provided target keys.
     * This implementation is optimized for multiple target keys. Since RFC 8259 dictates that JSON exchanges between
     * systems that are not part of an enclosed system MUST be encoded using UTF-8, this method assumes UTF-8 encoding.
     *
     * @param input the input message for which values might be masked
     * @return the masked message
     */
    @Override
    public byte[] mask(byte[] input) {
        try {
            MaskingState maskingState = new MaskingState(input, !maskingConfig.getTargetJsonPaths().isEmpty());

            KeyMaskingConfig keyMaskingConfig = maskingConfig.isInAllowMode() ? maskingConfig.getDefaultConfig() : null;
            if (maskingState.jsonPathEnabled()) {
                maskingState.expandCurrentJsonPath(keyMatcher.getJsonPathRootNode());
                keyMaskingConfig = keyMatcher.getMaskConfigIfMatched(maskingState.getMessage(), -1, -1, maskingState.getCurrentJsonPathNode());
            }

            stepOverWhitespaceCharacters(maskingState);
            visitValue(maskingState, keyMaskingConfig);

            return maskingState.flushReplacementOperations();
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new InvalidJsonException("Invalid JSON input provided: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * Entrypoint of visiting any value (object, array or primitive) in the JSON.
     *
     * @param maskingState     the current masking state
     * @param keyMaskingConfig if not null it means that the current value is being masked otherwise the value is not
     *                         being masked
     */
    private void visitValue(MaskingState maskingState, @Nullable KeyMaskingConfig keyMaskingConfig) {
        // using switch-case over 'if'-statements to improve performance by ~20% (measured in benchmarks)
        switch (maskingState.byteAtCurrentIndex()) {
            case '[' -> visitArray(maskingState, keyMaskingConfig);
            case '{' -> visitObject(maskingState, keyMaskingConfig);
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                if (keyMaskingConfig != null) {
                    maskNumber(maskingState, keyMaskingConfig);
                } else {
                    stepOverNumericValue(maskingState);
                }
            }
            case '"' -> {
                if (keyMaskingConfig != null) {
                    maskString(maskingState, keyMaskingConfig);
                } else {
                    stepOverStringValue(maskingState);
                }
            }
            case 't' -> {
                if (keyMaskingConfig != null) {
                    maskBoolean(maskingState, keyMaskingConfig);
                } else {
                    maskingState.incrementIndex(4);
                }
            }
            case 'f' -> {
                if (keyMaskingConfig != null) {
                    maskBoolean(maskingState, keyMaskingConfig);
                } else {
                    maskingState.incrementIndex(5);
                }
            }
            case 'n' -> maskingState.incrementIndex(4);
            default -> { /* return */ }
        }
    }

    /**
     * Visits an array of unknown values (or empty) and invokes {@link #visitValue(MaskingState, KeyMaskingConfig)} on
     * each element while propagating the {@link KeyMaskingConfig}.
     *
     * @param maskingState     the current {@link MaskingState}
     * @param keyMaskingConfig if not null it means that the current value is being masked according to the
     *                         {@link KeyMaskingConfig}. Otherwise, the value is not masked
     */
    private void visitArray(MaskingState maskingState, @Nullable KeyMaskingConfig keyMaskingConfig) {
        maskingState.expandCurrentJsonPath(keyMatcher.traverseJsonPathSegment(maskingState.getMessage(), maskingState.getCurrentJsonPathNode(), -1, -1));
        while (maskingState.next()) {
            stepOverWhitespaceCharacters(maskingState);
            // check if we're in an empty array
            if (maskingState.byteAtCurrentIndex() == ']') {
                break;
            }

            visitValue(maskingState, keyMaskingConfig);

            stepOverWhitespaceCharacters(maskingState);
            // check if we're at the end of a (non-empty) array
            if (maskingState.byteAtCurrentIndex() == ']') {
                break;
            }
        }
        maskingState.next(); // step over array closing square bracket
        maskingState.backtrackCurrentJsonPath();
    }

    /**
     * Visits an object, iterates over the keys and checks whether key needs to be masked (if
     * {@link JsonMaskingConfig.TargetKeyMode#MASK}) or allowed (if {@link JsonMaskingConfig.TargetKeyMode#ALLOW}). For
     * each value, invokes {@link #visitValue(MaskingState, KeyMaskingConfig)} with a non-null {@link KeyMaskingConfig}
     * (when key needs to be masked) or {@code null} (when key is allowed). Whenever 'parentKeyMaskingConfig' is
     * supplied, it means that the object with all its keys is being masked. The only situation when the individual
     * values do not need to be masked is when the key is explicitly allowed (in allow mode).
     *
     * @param maskingState           the current {@link MaskingState}
     * @param parentKeyMaskingConfig if not null it means that the current value is being masked according to the
     *                               {@link KeyMaskingConfig}. Otherwise, the value is not being masked
     */
    private void visitObject(MaskingState maskingState, @Nullable KeyMaskingConfig parentKeyMaskingConfig) {
        while (maskingState.next()) {
            stepOverWhitespaceCharacters(maskingState);
            // check if we're in an empty object
            if (maskingState.byteAtCurrentIndex() == '}') {
                break;
            }
            // In case target keys should be considered as allow list, we need to NOT mask certain keys
            int openingQuoteIndex = maskingState.currentIndex();

            stepOverStringValue(maskingState);

            int afterClosingQuoteIndex = maskingState.currentIndex();
            int keyLength = afterClosingQuoteIndex - openingQuoteIndex - 2; // minus the opening and closing quotes
            maskingState.expandCurrentJsonPath(keyMatcher.traverseJsonPathSegment(maskingState.getMessage(), maskingState.getCurrentJsonPathNode(), openingQuoteIndex + 1, keyLength));
            KeyMaskingConfig keyMaskingConfig = keyMatcher.getMaskConfigIfMatched(maskingState.getMessage(), openingQuoteIndex + 1, // plus one for the opening quote
                    keyLength, maskingState.getCurrentJsonPathNode());
            stepOverWhitespaceCharacters(maskingState);
            // step over the colon ':'
            maskingState.next();
            stepOverWhitespaceCharacters(maskingState);

            // if we're in the allow mode, then getting a null as config, means that the key has been explicitly
            // allowed and must not be masked, even if enclosing object is being masked
            boolean valueAllowed = maskingConfig.isInAllowMode() && keyMaskingConfig == null;
            if (valueAllowed) {
                stepOverValue(maskingState);
            } else {
                // this is where it might get confusing - this method is called when the whole object is being masked
                // if we got a maskingConfig for the key - we need to mask this key with that config. However, if the config
                // we got was the default config, then it means that the key doesn't have a specific configuration and
                // we should fall back to key specific config that the object is being masked with.
                // E.g.: '{ "a": { "b": "value" } }' we want to use config of 'b' if any, but fallback to config of 'a'
                if (parentKeyMaskingConfig != null && (keyMaskingConfig == null || keyMaskingConfig == maskingConfig.getDefaultConfig())) {
                    keyMaskingConfig = parentKeyMaskingConfig;
                }
                visitValue(maskingState, keyMaskingConfig);
            }
            maskingState.backtrackCurrentJsonPath();

            stepOverWhitespaceCharacters(maskingState);
            // check if we're at the end of a (non-empty) object
            if (maskingState.byteAtCurrentIndex() == '}') {
                break;
            }
        }
        // step over closing curly bracket ending the object
        maskingState.next();
    }

    /**
     * Masks the string value in the message of the {@link MaskingState}, starting from the current index which should
     * be at the opening quote of the string value.
     * <p>
     * When the method returns, the current index in the masking state is one position after the latest byte which was
     * part of the masked string value.
     *
     * @param maskingState     the current {@link MaskingState} for which the current index must correspond to the
     *                         opening quote of the string value in the input array
     * @param keyMaskingConfig the {@link KeyMaskingConfig} for the corresponding JSON key
     */
    private void maskString(MaskingState maskingState, KeyMaskingConfig keyMaskingConfig) {
        maskingState.registerValueStartIndex();
        stepOverStringValue(maskingState);

        keyMaskingConfig.getStringValueMasker().maskValue(maskingState);

        maskingState.clearValueStartIndex();
    }

    /**
     * Masks the numeric value in the message of the {@link MaskingState}, starting from the current index which should
     * be at the first numeric character of numeric value.
     * <p>
     * When the method returns, the current index in the masking state is one position after the latest byte which was
     * part of the masked number.
     *
     * @param maskingState     the current {@link MaskingState} for which the current index must correspond to the first
     *                         numeric character of the numeric value in the input array
     * @param keyMaskingConfig the {@link KeyMaskingConfig} for the corresponding JSON key
     */
    private void maskNumber(MaskingState maskingState, KeyMaskingConfig keyMaskingConfig) {
        // This block deals with numeric values
        maskingState.registerValueStartIndex();
        stepOverNumericValue(maskingState);

        keyMaskingConfig.getNumberValueMasker().maskValue(maskingState);

        maskingState.clearValueStartIndex();
    }

    /**
     * Masks the boolean value in the message of the {@link MaskingState}, starting from the current index which should
     * be at the first character of the boolean value.
     *
     * @param maskingState     the current {@link MaskingState} for which the current index must correspond to the first
     *                         character of the boolean value in the input array
     * @param keyMaskingConfig the {@link KeyMaskingConfig} for the corresponding JSON key
     */
    private void maskBoolean(MaskingState maskingState, KeyMaskingConfig keyMaskingConfig) {
        maskingState.registerValueStartIndex();
        maskingState.incrementIndex(AsciiCharacter.isLowercaseT(maskingState.byteAtCurrentIndex()) ? 4 : 5);

        keyMaskingConfig.getBooleanValueMasker().maskValue(maskingState);

        maskingState.clearValueStartIndex();
    }

    /**
     * This method assumes the masking state is currently at the first byte of a JSON value which can be any of: array,
     * boolean, object, null, number, or string and increments the current index in the masking state until the current
     * index is one position after the value.
     * <p>
     * Note: in case the value is an object or array, it steps over the entire object and array and all the elements it
     * includes (e.g. nested arrays, objects, etc.).
     */
    private static void stepOverValue(MaskingState maskingState) {
        switch (maskingState.byteAtCurrentIndex()) {
            case '"' -> stepOverStringValue(maskingState);
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> stepOverNumericValue(maskingState);
            case 't', 'n' -> maskingState.incrementIndex(4); // true or null
            case 'f' -> maskingState.incrementIndex(5); // false
            case '{' -> stepOverObject(maskingState);
            case '[' -> stepOverArray(maskingState);
            default -> { /* return */ }
        }
    }

    /**
     * Checks if the byte at the current index in the {@link MaskingState} is a white space character and if so,
     * increments the index by one. Returns as soon as the byte at the current index in the masking state is not a white
     * space character.
     *
     * @param maskingState the current {@link MaskingState}
     */
    private static void stepOverWhitespaceCharacters(MaskingState maskingState) {
        while (!maskingState.endOfJson() && AsciiJsonUtil.isWhiteSpace(maskingState.byteAtCurrentIndex())) {
            maskingState.next();
        }
    }

    /**
     * This method assumes the masking state is currently at the first numeric character of a numeric value and
     * increments the current index in the masking state until the current index is one position after the numeric
     * value.
     */
    private static void stepOverNumericValue(MaskingState maskingState) {
        do {
            maskingState.next();
        } while (maskingState.currentIndex() < maskingState.getMessage().length && AsciiJsonUtil.isNumericCharacter(maskingState.byteAtCurrentIndex()));
    }

    /**
     * This method assumes the masking state is currently at the opening quote of a string value and increments the
     * current index in the masking state until the current index is one position after the string (including the double
     * quote).
     *
     * @param maskingState the current {@link MaskingState}
     */
    private static void stepOverStringValue(MaskingState maskingState) {
        boolean isEscapeCharacter = false;
        while (maskingState.next()) {
            if (!isEscapeCharacter && maskingState.byteAtCurrentIndex() == '"') {
                maskingState.next();  // step over the closing quote
                break;
            }
            isEscapeCharacter = !isEscapeCharacter && maskingState.byteAtCurrentIndex() == '\\';
        }
    }

    /**
     * This method assumes the masking state is currently at the opening curly bracket of an object value and increments
     * the current index in the masking state until the current index is one position after the closing curly bracket of
     * the object.
     */
    private static void stepOverObject(MaskingState maskingState) {
        // step over opening curly bracket
        maskingState.next();
        int objectDepth = 1;
        while (objectDepth > 0) {
            // We need to specifically step over strings to not consider curly brackets which are part of a string
            // this will expand until the end of unescaped double quote, so we're guaranteed to never have unescaped
            // quote in this condition
            if (AsciiCharacter.isDoubleQuote(maskingState.byteAtCurrentIndex())) {
                // this makes sure that we step over curly brackets (open and close) which are part of strings
                stepOverStringValue(maskingState);
            } else {
                if (AsciiCharacter.isCurlyBracketOpen(maskingState.byteAtCurrentIndex())) {
                    objectDepth++;
                } else if (AsciiCharacter.isCurlyBracketClose(maskingState.byteAtCurrentIndex())) {
                    objectDepth--;
                }
                maskingState.next();
            }
        }
    }

    /**
     * This method assumes the masking state is currently at the opening square bracket of an array value and increments
     * the current index in the masking state until the current index is one position after the closing square bracket
     * of the array.
     */
    private static void stepOverArray(MaskingState maskingState) {
        // step over opening square bracket
        maskingState.next();
        int arrayDepth = 1;
        while (arrayDepth > 0) {
            // We need to specifically step over strings to not consider square brackets which are part of a string
            // this will expand until the end of unescaped double quote, so we're guaranteed to never have unescaped
            // quote in this condition
            if (AsciiCharacter.isDoubleQuote(maskingState.byteAtCurrentIndex())) {
                stepOverStringValue(maskingState);
            } else {
                if (AsciiCharacter.isSquareBracketOpen(maskingState.byteAtCurrentIndex())) {
                    arrayDepth++;
                } else if (AsciiCharacter.isSquareBracketClose(maskingState.byteAtCurrentIndex())) {
                    arrayDepth--;
                }
                maskingState.next();
            }
        }
    }
}
