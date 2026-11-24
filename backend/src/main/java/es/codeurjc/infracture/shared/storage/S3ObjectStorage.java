package es.codeurjc.infracture.shared.storage;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

// Stores objects in one bucket of an S3-compatible server. The bucket is created on the first write, so
// the backend starts without the server and reads before any write find nothing.
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client client;
    private final String bucket;
    private volatile boolean bucketReady;

    public S3ObjectStorage(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        call(() -> {
            ensureBucket();
            return client.putObject(request -> request.bucket(bucket).key(key).contentType(contentType),
                    RequestBody.fromBytes(content));
        });
    }

    @Override
    public Optional<StoredObject> get(String key) {
        return call(() -> {
            try {
                ResponseBytes<GetObjectResponse> object = client
                        .getObjectAsBytes(request -> request.bucket(bucket).key(key));
                return Optional.of(new StoredObject(object.asByteArray(), object.response().contentType()));
            } catch (NoSuchKeyException | NoSuchBucketException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public void delete(String key) {
        call(() -> {
            try {
                return client.deleteObject(request -> request.bucket(bucket).key(key));
            } catch (NoSuchBucketException e) {
                // Nothing was ever written, so there is nothing to delete.
                return null;
            }
        });
    }

    @Override
    public List<String> keys(String prefix) {
        return call(() -> {
            try {
                return client.listObjectsV2Paginator(request -> request.bucket(bucket).prefix(prefix))
                        .contents().stream().map(S3Object::key).toList();
            } catch (NoSuchBucketException e) {
                return List.of();
            }
        });
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        try {
            client.createBucket(request -> request.bucket(bucket));
        } catch (BucketAlreadyOwnedByYouException e) {
            // Created earlier, by this or a previous run.
        }
        bucketReady = true;
    }

    // The SDK types stay in this class; callers only see that the storage is unavailable.
    private static <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (SdkException e) {
            throw new StorageUnavailableException(e);
        }
    }
}
