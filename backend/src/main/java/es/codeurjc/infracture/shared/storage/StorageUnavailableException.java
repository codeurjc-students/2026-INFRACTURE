package es.codeurjc.infracture.shared.storage;

// The storage server is down, unreachable or rejected the request; the operation may succeed later.
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(Throwable cause) {
        super("Image storage is unavailable", cause);
    }
}
