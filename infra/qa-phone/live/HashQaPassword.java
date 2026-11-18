import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class HashQaPassword {
    public static void main(String[] args) throws Exception {
        var encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        var password = Files.readString(Path.of(args[0])).strip();
        var hash = encoder.encode(password);
        if (!encoder.matches(password, hash)) {
            throw new IllegalStateException("Argon2 verification failed");
        }
        System.out.print("{argon2}" + hash);
    }
}
