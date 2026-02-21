error id: file:///C:/GANTone/backend/src/main/java/com/example/GANTone/KafkaConfig.java:_empty_/KafkaTemplate#
file:///C:/GANTone/backend/src/main/java/com/example/GANTone/KafkaConfig.java
empty definition using pc, found symbol in pc: _empty_/KafkaTemplate#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 4745
uri: file:///C:/GANTone/backend/src/main/java/com/example/GANTone/KafkaConfig.java
text:
```scala
package com.example.GANTone;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Базовый протокол
        configProps.put("security.protocol", "SSL");
        
        // 1. Trust Store (Твой CA сертификат текстом)
        configProps.put("ssl.truststore.type", "PEM");
        configProps.put("ssl.truststore.certificates", 
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIERDCCAqygAwIBAgIUX2+dZFgO7TOL1mr0kDEKpG7s3JwwDQYJKoZIhvcNAQEM\n" +
            "BQAwOjE4MDYGA1UEAwwvNGRiNzk3MGYtZTljOC00OWU0LWI0NDQtNzg0OGE1YTY5\n" +
            "Mzc4IFByb2plY3QgQ0EwHhcNMjYwMjIwMTkwNzA0WhcNMzYwMjE4MTkwNzA0WjA6\n" +
            "MTgwNgYDVQQDDC80ZGI3OTcwZi1lOWM4LTQ5ZTQtYjQ0NC03ODQ4YTVhNjkzNzgg\n" +
            "UHJvamVjdCBDQTCCAaIwDQYJKoZIhvcNAQEBBQADggGPADCCAYoCggGBAOjBPIGV\n" +
            "ibl13jVwlXa7MgaEUtHxwTH0WxfyD4wmaogqa6uGotpmqiVxTGSHwQ4kGZTbOYSZ\n" +
            "Rqa5nrkd0vsl07yTTE/XNDiiEEbvda5EkJ6QzgES/Y98HXfnUZfGKpa0OS4EKVFY\n" +
            "6Z08U6yMin8w1/nWyF4gLq8tbceu/3snYR8IZMYwsjiamt/A+t/qnSBdJXiLhpLK\n" +
            "x3Dc6iqxSn8LiO/IvHkPLwZ8xQcllY++VYqhl531bxpb8j91LKPZsUMGhLfo6it9\n" +
            "ymJKC3PyWFaVUFlC+PCn1ES/ZpoiFca48yK4gtrm7pxTUO/Q0yHVtcZc/yNH+Jmc\n" +
            "8qwU0UpPhM+Bm3dRKTNsR4qHLvjSzuCjMHsYR3CZnvqwbmufNgxhg9R2u0oDpEU+\n" +
            "ZKhxHBJI9i2nzarLu3yHpNuOGXdfbZnYCStxO+82SCp3TSD1l+2mA2TzchPwPlJr\n" +
            "CHG5eztXKfAyR2TR/fgWGqhgLRhU3Cl6mEvvg5A/VrNARgJaALburFd50QIDAQAB\n" +
            "o0IwQDAdBgNVHQ4EFgQUmwdJhweXdpCqSqXOfq1wDteeChkwEgYDVR0TAQH/BAgw\n" +
            "BgEB/wIBADALBgNVHQ8EBAMCAQYwDQYJKoZIhvcNAQEMBQADggGBALSWTWbD5eYm\n" +
            "zbOHbEMawn2SOZOAr47DmaxIPW320dltgKWb7Y9mHZu2RMraqljNav5dfJ96QnzH\n" +
            "jQz0Sp64Vyf0twMHkIhHtqemS7/kbGSTnXFJWF1Ws20xmhpVSz35eNFud0jESNWz\n" +
            "QJObqDx8BYIJkYzhgDmcKAkFd7NRaAEElZ/FDeUkdT+0JAy9d+sa7VNVo1QfB9th\n" +
            "9Cv8BOWn6GVKDNLYLUxiYqvP+XB0BODscLGN0mS+zRkgSaMbUpZW8zsmsuVjGvmH\n" +
            "y0t67oecnldHyf33JIm6hIbTk7UhzqCbdwaTOMXsF1LDqnC5MNQW3DF/yyxrBlLH\n" +
            "IOxlPIMtuyuEh37/i7/jOYxuVsUpx17K3xsKrB+iKVdz+6a1gwEMGm0jD5uZnC/Y\n" +
            "5fTdFQ8OzfaKAPmj26nECBVNfV7D3pRzpLgUV/0++SbveG0Mk9T6WC+hLTgoSkso\n" +
            "e1tSlA5aveWSi0c0fzR5h0XzElqDj6KRGLKlkKwWAGENB7hDuiZpqA==\n" +
            "-----END CERTIFICATE-----"
        );

        // 2. Key Store (Магия с временным файлом для p12)
        try {
            Path tempKeystore = Files.createTempFile("keystore", ".p12");
            try (InputStream is = new ClassPathResource("keystore.p12").getInputStream()) {
                Files.copy(is, tempKeystore, StandardCopyOption.REPLACE_EXISTING);
            }
            
            configProps.put("ssl.keystore.location", tempKeystore.toAbsolutePath().toString());
            configProps.put("ssl.keystore.type", "PKCS12");
            configProps.put("ssl.keystore.password", "GANTonePass");
            configProps.put("ssl.key.password", "GANTonePass");
            
            // Удаляем файл при выходе приложения
            tempKeystore.toFile().deleteOnExit();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to load keystore.p12 from resources", e);
        }

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate@@<>(producerFactory());
    }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/KafkaTemplate#