package gregochr.services.sse;

import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * An SseEmitter that records what is sent as the text that would go on the wire, can be told to fail every send,
 * and exposes the lifecycle callbacks the service registers so a test can fire them.
 */
class RecordingSseEmitter extends SseEmitter {
    private final List<String> sent = new ArrayList<>();
    private IOException failure;
    private Runnable completionCallback;
    private Runnable timeoutCallback;
    private Consumer<Throwable> errorCallback;
    private int completeCalls;

    RecordingSseEmitter(Long timeout) {
        super(timeout);
    }

    RecordingSseEmitter failingWith(IOException failure) {
        this.failure = failure;
        return this;
    }

    @Override
    public void send(SseEventBuilder builder) throws IOException {
        if (failure != null) {
            throw failure;
        }
        StringBuilder wire = new StringBuilder();
        for (DataWithMediaType part : builder.build()) {
            wire.append(part.getData());
        }
        sent.add(wire.toString());
    }

    @Override
    public synchronized void complete() {
        completeCalls++;
    }

    @Override
    public synchronized void onCompletion(Runnable callback) {
        this.completionCallback = callback;
    }

    @Override
    public synchronized void onTimeout(Runnable callback) {
        this.timeoutCallback = callback;
    }

    @Override
    public synchronized void onError(Consumer<Throwable> callback) {
        this.errorCallback = callback;
    }

    List<String> sent() {
        return sent;
    }

    int completeCalls() {
        return completeCalls;
    }

    void fireCompletion() {
        completionCallback.run();
    }

    void fireTimeout() {
        timeoutCallback.run();
    }

    void fireError(Throwable error) {
        errorCallback.accept(error);
    }
}
