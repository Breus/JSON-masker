package masker;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class JsonMasker extends AbstractMasker {
    @NotNull
    public static JsonMasker getMaskerWithTargetKey(@NotNull String targetKey) {
        if (targetKey.length() < 1) {
            throw new IllegalArgumentException("Target key must contain at least one character");
        }
        return new JsonMasker(targetKey);
    }

    @Override
    public byte[] mask(byte[] message, @NotNull Charset charset) {
        return maskValuesOfTargetKey(new String(message, charset)).getBytes(charset);
    }

    @Override
    @NotNull
    public String mask(@NotNull String message) {
        return maskValuesOfTargetKey(message);
    }

    private JsonMasker(@NotNull String targetKey) {
        super("\"" + targetKey + "\"", targetKey.length()+2);
    }

    @NotNull
    String maskValuesOfTargetKey(@NotNull String input) {
        return maskValuesOfTargetKey("", input);
    }

    @NotNull
    String maskValuesOfTargetKey(@NotNull String prefix, @NotNull String input) {
        int startIndexOfFilterKey = input.indexOf(super.getTargetKey());
        if (startIndexOfFilterKey == -1) {
            return prefix + input; // input doesn't contain filter key anymore, no further masking required
        }
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        int colonIndex = 0;
        int i = startIndexOfFilterKey + super.getTargetKeyLength();
        for (; i < inputBytes.length; i++) {
            if (inputBytes[i] == getByteValueOfUTF8String(":")) {
                colonIndex = i;
                break;
            }
            if (inputBytes[i] == getByteValueOfUTF8String(" ")) {
                continue;
            }
            break; // found a different character than whitespace or colon, so the found target key is not a JSON key
        }
        if (colonIndex == 0) {
            return maskValuesOfTargetKey(prefix + input.substring(0,i), input.substring(i)); // input contained filter key, but it wasn't a JSON key, so continue on the tail
        }
        i++; // step over colon
        for (; i < inputBytes.length; i++) {
            if (inputBytes[i] == getByteValueOfUTF8String("\"")) {
                i++; // step over quote
                while(inputBytes[i] != getByteValueOfUTF8String("\"")) {
                    inputBytes[i] = getByteValueOfUTF8String("*");
                    i++;
                }
                break;
            }
            if (inputBytes[i] == getByteValueOfUTF8String(" ")) {
                continue;
            }
            return maskValuesOfTargetKey(prefix + input.substring(0, i), input.substring(i));
        }
        return prefix + new String(inputBytes, StandardCharsets.UTF_8);
    }

     byte getByteValueOfUTF8String(@NotNull String inputStringCharacter) {
        if (inputStringCharacter.length() != 1) {
            throw new IllegalArgumentException("This method should only be called for Strings which are only a single byte in UTF-8");
        }
        return inputStringCharacter.getBytes(StandardCharsets.UTF_8)[0];
    }
}