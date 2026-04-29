package les13.finguide.backend.api;

public record ApiEnvelope<T>(T data) {
    public static <T> ApiEnvelope<T> of(T data) {
        return new ApiEnvelope<>(data);
    }
}
