package cl.dnl.intranet.ubb_scraper.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class EncryptionService {

    private final AesBytesEncryptor encryptor;

    // El constructor inyecta el valor de 'app.encryption.key' desde application.properties.
    public EncryptionService(@Value("${app.encryption.key}") String secretKey,
                             @Value("${app.encryption.salt}") String salt) {
        // Usamos el algoritmo AES/GCM, que es el estándar recomendado para encriptación.
        // Requiere una clave secreta y un "salt" (un valor aleatorio para añadir más seguridad).
        this.encryptor = new AesBytesEncryptor(secretKey, salt, AesBytesEncryptor.CipherAlgorithm.GCM.defaultIvGenerator());
    }

    /**
     * Encripta un texto plano.
     * @param plainText El texto a encriptar (ej. la contraseña).
     * @return Un string en formato Base64 que representa el texto encriptado.
     */
    public String encrypt(String plainText) {
        byte[] encryptedBytes = encryptor.encrypt(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * Desencripta un texto que fue encriptado previamente con este servicio.
     * @param encryptedText El string en Base64 a desencriptar.
     * @return El texto plano original.
     */
    public String decrypt(String encryptedText) {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
        byte[] decryptedBytes = encryptor.decrypt(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}