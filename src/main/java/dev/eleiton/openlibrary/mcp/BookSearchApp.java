package dev.eleiton.openlibrary.mcp;

import dev.eleiton.openlibrary.client.OpenLibraryClient;
import dev.eleiton.openlibrary.model.BookResults;
import dev.eleiton.openlibrary.model.SearchCriteria;
import dev.eleiton.openlibrary.model.SearchResponse;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.MetaProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BookSearchApp {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 6;
    private static final int OVERFETCH_FACTOR = 2;
    private static final int MAX_AUTHORS_PER_BOOK = 1;
    private static final int MAX_SUBJECTS_PER_BOOK = 3;
    private static final String UI_RESOURCE_URI = "ui://books/search-results.html";

    private final OpenLibraryClient client;

    @Value("classpath:/app/search-results.html")
    private Resource searchUi;

    public BookSearchApp(OpenLibraryClient client) {
        this.client = client;
    }

    //
    // UI resource - HTML rendered by the MCP host
    //
    @McpResource(
            name = "Book Search UI",
            uri = UI_RESOURCE_URI,
            mimeType = "text/html;profile=mcp-app",
            metaProvider = CspMetaProvider.class)
    public String getSearchUi() throws IOException {
        return searchUi.getContentAsString(StandardCharsets.UTF_8);
    }

    // Tells the host "when rendering this HTML, allow these external domains in the Content Security Policy."
    public static final class CspMetaProvider implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return Map.of("ui",
                    Map.of("csp",
                            Map.of("resourceDomains",
                                    List.of(
                                            "https://unpkg.com",
                                            "https://covers.openlibrary.org",
                                            "https://archive.org",
                                            "https://*.archive.org"))));
        }
    }

    //
    // UI-opening tool - the LLM calls this; host renders the linked HTML
    //
    @McpTool(
            name = "search-books",
            title = "Search Open Library",
            description = """
                    Search Open Library and show matching books in a UI panel \
                    (cover, title, author, plus chips for further searches). \
                    Provide at least one of: query, title, author, author_key, subject, isbn, language.
                    
                    FIELD SELECTION: all parameters are ANDed by the API, so use as few as needed. \
                    Each field accepts a single value only. Never attempt to express multiple authors \
                    or subjects in one call. For genre or theme intent (e.g. "space opera", \
                    "gothic horror"), infer the best matching OpenLibrary subject term and pass it as \
                    `subject`. Reserve `query` for keyword lookups that don't fit any other field: \
                    partial or uncertain titles, proper names, or highly specific terms with no subject \
                    taxonomy entry. Never combine `query` and `subject` — they are ANDed and will \
                    over-restrict results.
                    
                    CONVERSATIONAL REFINEMENT: when the user's message refines or narrows the \
                    previous book search (e.g. 'now by Tolkien', 'only in Spanish', 'recent ones', \
                    'and about dragons'), pass ALL of the previously-applied parameters PLUS the new \
                    one — do not start over. After each render the panel posts its current filters \
                    into the model context; use that as the baseline. Only drop the previous filters \
                    when the user clearly switches topic (e.g. 'now show me books about WWII').
                    
                    CHIP CLICKS post a follow-up like 'Search books with author_key=OL...A AND \
                    subject="Fantasy"' — pass each name=value pair as the corresponding tool \
                    argument (quoted values are strings).""",
            metaProvider = SearchBooksMetaProvider.class)
    public BookResults searchBooks(
            @McpToolParam(description = "Keyword fallback: use only for partial/uncertain titles, proper names, or terms with no subject taxonomy entry. Never combine with `subject`.", required = false) String query,
            @McpToolParam(description = "Book title", required = false) String title,
            @McpToolParam(description = "Author name. Prefer this over author_key.", required = false) String author,
            @McpToolParam(description = "Genre, theme, or topic inferred from user intent (e.g. 'space opera', 'gothic horror'). Prefer this over `query` for thematic searches. Never combine with `query`.", required = false) String subject,
            @McpToolParam(description = "ISBN", required = false) String isbn,
            @McpToolParam(description = "Three-letter MARC language code (eng, spa, fre, ger, ita, jpn, por, rus, chi, ara, etc.)", required = false) String language,
            @McpToolParam(description = "Max results 1-6, default 3", required = false) Integer limit) {
        return doSearch(query, title, author, subject, isbn, language, limit);
    }

    // Tells the host "when this tool result arrives, open the panel at this URI."
    public static final class SearchBooksMetaProvider implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return Map.of("ui", Map.of("resourceUri", UI_RESOURCE_URI));
        }
    }

    private BookResults doSearch(String query, String title, String author,
                                 String subject, String isbn, String language, Integer limit) {
        SearchCriteria criteria = buildCriteria(query, title, author, subject, isbn, language, limit);
        return toResults(criteria, client.search(criteria, criteria.limit() * OVERFETCH_FACTOR));
    }

    private static SearchCriteria buildCriteria(
            String query, String title, String author,
            String subject, String isbn, String language, Integer limit) {
        int clamped = clampLimit(limit);
        SearchCriteria criteria = new SearchCriteria(
                trim(query), trim(title), trim(author),
                trim(subject), trim(isbn), trim(language), clamped);
        if (!criteria.hasAnyField()) {
            throw new IllegalArgumentException(
                    "At least one search field is required: query, title, author, author_key, subject, isbn, or language.");
        }
        return criteria;
    }

    private static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    static BookResults toResults(SearchCriteria criteria, SearchResponse response) {
        List<SearchResponse.Doc> docs = response.docs() == null ? List.of() : response.docs();
        List<BookResults.BookCard> cards = filterEligible(docs).stream()
                .limit(criteria.limit())
                .map(BookSearchApp::toCard)
                .toList();
        return new BookResults(criteria, response.numFound(), cards);
    }

    private static List<SearchResponse.Doc> filterEligible(List<SearchResponse.Doc> docs) {
        return docs.stream()
                .filter(doc -> doc.coverId() != null)
                .toList();
    }

    private static BookResults.BookCard toCard(SearchResponse.Doc doc) {
        List<BookResults.Author> authors = new ArrayList<>();
        List<String> names = doc.authorNames() == null ? List.of() : doc.authorNames();
        List<String> keys = doc.authorKeys() == null ? List.of() : doc.authorKeys();
        int authorCount = Math.min(MAX_AUTHORS_PER_BOOK, names.size());
        for (int i = 0; i < authorCount; i++) {
            String key = i < keys.size() ? keys.get(i) : null;
            authors.add(new BookResults.Author(names.get(i), key));
        }
        List<String> subjects = doc.subjects() == null
                ? List.of()
                : doc.subjects().stream().limit(MAX_SUBJECTS_PER_BOOK).toList();
        String coverUrl = doc.coverId() == null
                ? null
                : "https://covers.openlibrary.org/b/id/" + doc.coverId() + "-L.jpg";
        return new BookResults.BookCard(doc.key(), doc.title(), authors, coverUrl, subjects);
    }
}
