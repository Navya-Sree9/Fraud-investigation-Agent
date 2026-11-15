package com.capstone.fraudcase.knowledge;

import com.capstone.fraudcase.knowledge.PolicyDocument.Availability;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * Validates, chunks and embeds approved synthetic policies at startup. Withdrawn, superseded and not-yet-effective
 * versions are kept for display but excluded from the vector store, so they can never be cited.
 */
@Service
public class PolicyIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PolicyIngestionService.class);

    private final EmbeddingProvider embeddings;
    private final PolicyVectorStore store;
    private final String location;
    private volatile List<PolicyDocument> documents = List.of();
    private volatile List<String> rejected = List.of();
    private volatile String failure;

    @Autowired
    public PolicyIngestionService(EmbeddingProvider embeddings, PolicyVectorStore store) {
        this(embeddings, store, "classpath*:policies/*.md");
    }

    PolicyIngestionService(EmbeddingProvider embeddings, PolicyVectorStore store, String location) {
        this.embeddings = embeddings;
        this.store = store;
        this.location = location;
    }

    @PostConstruct
    public void ingest() {
        List<PolicyDocument> parsed = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources(location)) {
                try (InputStream in = resource.getInputStream()) {
                    parsed.add(PolicyDocumentParser.parse(resource.getFilename(), new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                } catch (PolicyDocumentParser.InvalidPolicyException e) {
                    invalid.add(e.getMessage());
                    log.warn("Rejected policy document: {}", e.getMessage());
                }
            }
            List<PolicyDocument> resolved = resolveAvailability(parsed, LocalDate.now());
            List<PolicyChunk> chunks = resolved.stream().filter(PolicyDocument::retrievable).flatMap(d -> d.chunks().stream()).toList();
            List<float[]> vectors = chunks.stream().map(c -> embeddings.embed(embeddingText(c))).toList();
            store.replaceAll(chunks, vectors);
            documents = resolved;
            rejected = invalid;
            failure = null;
            log.info("Ingested {} policy documents ({} retrievable chunks) using {}", resolved.size(), chunks.size(), embeddings.name());
        } catch (IOException | RuntimeException e) {
            failure = e.getClass().getSimpleName();
            log.error("Policy ingestion failed; retrieval will abstain as UNAVAILABLE ({})", failure);
        }
    }

    static List<PolicyDocument> resolveAvailability(List<PolicyDocument> parsed, LocalDate today) {
        Map<String, List<PolicyDocument>> byId = parsed.stream().collect(Collectors.groupingBy(PolicyDocument::documentId));
        List<PolicyDocument> resolved = new ArrayList<>();
        for (List<PolicyDocument> versions : byId.values()) {
            PolicyDocument latestActive = versions.stream()
                    .filter(d -> d.availability() == Availability.ACTIVE && !d.effectiveDate().isAfter(today))
                    .max(Comparator.comparing(PolicyDocument::version, PolicyIngestionService::compareVersions))
                    .orElse(null);
            for (PolicyDocument d : versions) {
                if (d.availability() == Availability.WITHDRAWN) {
                    resolved.add(d);
                } else if (d.effectiveDate().isAfter(today)) {
                    resolved.add(d.withAvailability(Availability.NOT_YET_EFFECTIVE));
                } else if (d != latestActive) {
                    resolved.add(d.withAvailability(Availability.SUPERSEDED));
                } else {
                    resolved.add(d);
                }
            }
        }
        resolved.sort(Comparator.comparing(PolicyDocument::documentId).thenComparing(PolicyDocument::version, PolicyIngestionService::compareVersions));
        return List.copyOf(resolved);
    }

    static int compareVersions(String a, String b) {
        int[] x = Arrays.stream(a.split("\\.")).mapToInt(Integer::parseInt).toArray();
        int[] y = Arrays.stream(b.split("\\.")).mapToInt(Integer::parseInt).toArray();
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int c = Integer.compare(i < x.length ? x[i] : 0, i < y.length ? y[i] : 0);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    static String embeddingText(PolicyChunk chunk) {
        return chunk.title() + " - " + chunk.section() + ": " + chunk.text();
    }

    public List<PolicyDocument> documents() {
        return documents;
    }

    public List<String> rejected() {
        return rejected;
    }

    public boolean healthy() {
        return failure == null && store.size() > 0;
    }

    public String failure() {
        return failure;
    }
}
