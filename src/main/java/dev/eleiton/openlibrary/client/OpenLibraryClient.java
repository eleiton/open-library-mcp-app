package dev.eleiton.openlibrary.client;

import dev.eleiton.openlibrary.model.SearchCriteria;
import dev.eleiton.openlibrary.model.SearchResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Component
public class OpenLibraryClient {

    private static final String FIELDS = "key,title,author_name,author_key,cover_i,subject";
    private static final String USER_AGENT = "BookSearchApp/0.1 (https://example.com/contact)";

    private final RestClient http;

    public OpenLibraryClient() {
        this(RestClient.builder());
    }

    public OpenLibraryClient(RestClient.Builder builder) {
        this.http = builder
                .baseUrl("https://openlibrary.org")
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    public SearchResponse search(SearchCriteria criteria, int fetchLimit) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/search.json")
                .queryParam("fields", FIELDS)
                .queryParam("limit", fetchLimit);
        addIfPresent(uri, "q", criteria.query());
        addIfPresent(uri, "title", criteria.title());
        addIfPresent(uri, "author", criteria.author());
        addIfPresent(uri, "subject", criteria.subject());
        addIfPresent(uri, "isbn", criteria.isbn());
        addIfPresent(uri, "language", criteria.language());

        URI built = uri.build().toUri();
        SearchResponse response = http.get()
                .uri(built)
                .retrieve()
                .body(SearchResponse.class);
        return response != null ? response : new SearchResponse(0, List.of());
    }

    private static void addIfPresent(UriComponentsBuilder uri, String name, String value) {
        if (SearchCriteria.notBlank(value)) {
            uri.queryParam(name, value);
        }
    }
}
