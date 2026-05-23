package dev.eleiton.openlibrary.model;

import java.util.List;

public record BookResults(SearchCriteria criteria, int totalFound, List<BookCard> books) {

    public record BookCard(
            String workKey,
            String title,
            List<Author> authors,
            String coverUrl,
            List<String> subjects) {
    }

    public record Author(String name, String key) {
    }
}
