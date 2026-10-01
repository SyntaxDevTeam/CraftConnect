package pl.syntaxdevteam.craftconnect.bridge.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/** Versioned, bounded messages shared by the Android client and server bridge. */
public final class AuthenticationBridgeProtocol {
    public static final String CHANNEL = "craftconnect:auth";
    private static final Set<String> PROVIDERS = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList("AuthMe", "nLogin", "AuthGatewayX")));
    private AuthenticationBridgeProtocol() { }

    public static byte[] subscribe(String nonce) {
        validateNonce(nonce);
        return encode("subscribe", nonce, "");
    }

    public static String subscriptionNonce(byte[] payload) {
        String[] fields = decode(payload, "subscribe");
        if (!fields[1].isEmpty()) throw new IllegalArgumentException("Unexpected provider");
        return fields[0];
    }

    public static byte[] authenticated(String nonce, Set<String> providers) {
        validateNonce(nonce);
        validateProviders(providers);
        return encode("authenticated", nonce, String.join(",", providers));
    }

    public static boolean confirms(byte[] payload, String nonce) {
        try {
            String[] fields = decode(payload, "authenticated");
            validateProviders(new HashSet<>(Arrays.asList(fields[1].split(","))));
            return fields[0].equals(nonce);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static byte[] encode(String kind, String nonce, String providers) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(1);
            output.writeUTF(kind);
            output.writeUTF(nonce);
            output.writeUTF(providers);
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String[] decode(byte[] payload, String kind) {
        if (payload.length > 256) throw new IllegalArgumentException("Message too large");
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
            if (input.readUnsignedByte() != 1 || !input.readUTF().equals(kind)) {
                throw new IllegalArgumentException("Unexpected message");
            }
            String nonce = input.readUTF();
            validateNonce(nonce);
            String providers = input.readUTF();
            if (input.available() != 0) throw new IllegalArgumentException("Trailing data");
            return new String[] {nonce, providers};
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid message", failure);
        }
    }

    private static void validateNonce(String nonce) {
        if (nonce.length() != 36 || !UUID.fromString(nonce).toString().equals(nonce)) {
            throw new IllegalArgumentException("Invalid connection nonce");
        }
    }

    private static void validateProviders(Set<String> providers) {
        if (providers.isEmpty() || !PROVIDERS.containsAll(providers)) {
            throw new IllegalArgumentException("Unknown authentication provider");
        }
    }
}
