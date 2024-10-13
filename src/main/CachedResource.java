package main;

import java.time.Instant;

public class CachedResource {
    private String content;
    private Instant lastModified;

    public CachedResource(String content, Instant lastModified) {
        this.content = content;
        this.lastModified = lastModified;
    }

    public String getContent() {
        return content;
    }

    public Instant getLastModified() {
        return lastModified;
    }
}
