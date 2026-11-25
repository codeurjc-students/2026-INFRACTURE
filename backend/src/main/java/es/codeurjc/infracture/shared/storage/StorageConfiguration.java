package es.codeurjc.infracture.shared.storage;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
class StorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client s3Client(@Value("${infracture.storage.endpoint}") URI endpoint,
            @Value("${infracture.storage.access-key}") String accessKey,
            @Value("${infracture.storage.secret-key}") String secretKey) {
        return client(endpoint, accessKey, secretKey);
    }

    @Bean
    ObjectStorage objectStorage(S3Client s3Client, @Value("${infracture.storage.bucket}") String bucket) {
        return new S3ObjectStorage(s3Client, bucket);
    }

    // Building the client opens no connection, so the backend starts even if the server is down.
    static S3Client client(URI endpoint, String accessKey, String secretKey) {
        return S3Client.builder()
                .endpointOverride(endpoint)
                // A local server has no bucket subdomains: the bucket goes in the path.
                .forcePathStyle(true)
                // S3 signs every request for a region; a local server accepts any.
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                // Send checksums only where S3 requires them, which every compatible server supports.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .build();
    }
}
