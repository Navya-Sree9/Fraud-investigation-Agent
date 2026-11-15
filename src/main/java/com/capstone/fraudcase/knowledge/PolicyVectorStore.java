package com.capstone.fraudcase.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * Minimal in-memory vector store. Scores are cosine similarity rescaled to [0, 1] via (cos + 1) / 2, the same
 * relevance-score convention used by common Java embedding stores, so thresholds stay comparable.
 */
@Component
public class PolicyVectorStore {

    private record Entry(PolicyChunk chunk, float[] vector) {
    }

    public record Match(PolicyChunk chunk, double score) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public void replaceAll(List<PolicyChunk> chunks, List<float[]> vectors) {
        List<Entry> fresh = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            fresh.add(new Entry(chunks.get(i), vectors.get(i)));
        }
        entries.clear();
        entries.addAll(fresh);
    }

    public int size() {
        return entries.size();
    }

    public List<Match> search(float[] query, int topK) {
        return entries.stream()
                .map(e -> new Match(e.chunk(), relevance(query, e.vector())))
                .sorted(Comparator.comparingDouble(Match::score).reversed())
                .limit(topK)
                .toList();
    }

    static double relevance(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        double cosine = (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
        return Math.round(((cosine + 1) / 2) * 10_000) / 10_000.0;
    }
}
