package dev.eleiton.openlibrary.model;

public record SearchCriteria(
        String query,
        String title,
        String author,
        String subject,
        String isbn,
        String language,
        int limit) {

    public boolean hasAnyField() {
        return notBlank(query)
                || notBlank(title)
                || notBlank(author)
                || notBlank(subject)
                || notBlank(isbn)
                || notBlank(language);
    }

    public static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
