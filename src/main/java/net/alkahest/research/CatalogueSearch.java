package net.alkahest.research;

import java.util.List;
import java.util.Locale;

/** Case-independent AND search across visible names, registry IDs, parts and essence names. */
public final class CatalogueSearch {
    private CatalogueSearch() { }

    public static boolean matches(String query, List<String> fields) {
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return true;
        List<String> haystack = fields.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        for (String term : normalized.split("\\s+")) {
            if (haystack.stream().noneMatch(field -> field.contains(term))) return false;
        }
        return true;
    }
}
