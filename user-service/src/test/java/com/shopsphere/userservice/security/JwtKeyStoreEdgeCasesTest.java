package com.shopsphere.userservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class JwtKeyStoreEdgeCasesTest {

    private Object invokePrivate(String methodName, Class<?>[] parameterTypes,
                                 Object... arguments) throws Exception {
        Method method = JwtKeyStore.class.getDeclaredMethod(
                methodName, parameterTypes);
        method.setAccessible(true);

        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException ex) {
            throw (Exception) ex.getCause();
        }
    }

    @Test
    void parsePrivateKey_rejectsPkcs1Format() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> invokePrivate(
                        "parsePrivateKey",
                        new Class<?>[]{String.class},
                        "-----BEGIN RSA PRIVATE KEY-----\ninvalid\n-----END RSA PRIVATE KEY-----"
                )
        );

        assertEquals(
                true,
                exception.getMessage().contains("PKCS#8")
        );
    }

    @Test
    void parsePrivateKey_rejectsInvalidBase64() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> invokePrivate(
                        "parsePrivateKey",
                        new Class<?>[]{String.class},
                        "not-valid-base64!"
                )
        );

        assertEquals(
                true,
                exception.getMessage().contains("valid PKCS#8 RSA private key")
        );
    }

    @Test
    void parsePublicKey_rejectsInvalidBase64() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> invokePrivate(
                        "parsePublicKey",
                        new Class<?>[]{String.class},
                        "not-valid-base64!"
                )
        );

        assertEquals(
                true,
                exception.getMessage().contains("invalid X.509 RSA public key")
        );
    }

    @Test
    void stripPem_removesPemHeadersWhitespaceAndLiteralNewlines()
            throws Exception {

        String result = (String) invokePrivate(
                "stripPem",
                new Class<?>[]{String.class},
                "-----BEGIN PUBLIC KEY-----\\nYWJj\\n-----END PUBLIC KEY-----"
        );

        assertEquals("YWJj", result);
    }
}