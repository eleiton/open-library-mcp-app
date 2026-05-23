package dev.eleiton.openlibrary.mcp;

import dev.eleiton.openlibrary.client.OpenLibraryClient;
import dev.eleiton.openlibrary.model.BookResults;
import dev.eleiton.openlibrary.model.SearchCriteria;
import dev.eleiton.openlibrary.model.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookSearchAppTest {

    private OpenLibraryClient client;
    private BookSearchApp app;

    @BeforeEach
    void setUp() {
        client = mock(OpenLibraryClient.class);
        app = new BookSearchApp(client);
        when(client.search(any(), anyInt())).thenReturn(new SearchResponse(0, List.of()));
    }

    @Test
    void searchBooksHitsClientAndReturnsResults() {
        SearchResponse.Doc doc = new SearchResponse.Doc(
                "/works/OL1W", "Book", List.of("Author"), List.of("OL1A"), 42, List.of("s"));
        when(client.search(any(), anyInt())).thenReturn(new SearchResponse(1, List.of(doc)));

        BookResults results = app.searchBooks("tolkien", null, null, null, null, null, null, 3);

        assertThat(results.books()).hasSize(1);
        assertThat(results.books().getFirst().title()).isEqualTo("Book");
    }

    @Test
    void searchBooksValidatesBlankFields() {
        assertThatThrownBy(() -> app.searchBooks(null, "  ", "", null, null, null, null, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one search field");
    }

    @Test
    void languageAloneIsAValidSearch() {
        app.searchBooks(null, null, null, null, null, null, "spa", 3);
        ArgumentCaptor<SearchCriteria> captor = ArgumentCaptor.forClass(SearchCriteria.class);
        verify(client).search(captor.capture(), anyInt());
        assertThat(captor.getValue().language()).isEqualTo("spa");
    }

    @Test
    void limitDefaultsToThreeAndClampsTo3() {
        app.searchBooks("tolkien", null, null, null, null, null, null, null);
        app.searchBooks("tolkien", null, null, null, null, null, null, 99);
        app.searchBooks("tolkien", null, null, null, null, null, null, -4);

        ArgumentCaptor<SearchCriteria> captor = ArgumentCaptor.forClass(SearchCriteria.class);
        verify(client, times(3)).search(captor.capture(), anyInt());
        List<SearchCriteria> calls = captor.getAllValues();
        assertThat(calls.get(0).limit()).isEqualTo(3);
        assertThat(calls.get(1).limit()).isEqualTo(6);
        assertThat(calls.get(2).limit()).isEqualTo(1);
    }

    @Test
    void eachFieldForwardedToClient() {
        app.searchBooks("q1", "t1", "a1", "OL1A", "s1", "isbn1", "eng", 2);

        ArgumentCaptor<SearchCriteria> captor = ArgumentCaptor.forClass(SearchCriteria.class);
        verify(client).search(captor.capture(), anyInt());
        SearchCriteria c = captor.getValue();
        assertThat(c.query()).isEqualTo("q1");
        assertThat(c.title()).isEqualTo("t1");
        assertThat(c.author()).isEqualTo("a1");
        assertThat(c.authorKey()).isEqualTo("OL1A");
        assertThat(c.subject()).isEqualTo("s1");
        assertThat(c.isbn()).isEqualTo("isbn1");
        assertThat(c.language()).isEqualTo("eng");
        assertThat(c.limit()).isEqualTo(2);
    }

    @Test
    void blankStringsAreNormalizedToNull() {
        app.searchBooks("q1", "  ", "", null, "  ", null, "  ", 3);

        ArgumentCaptor<SearchCriteria> captor = ArgumentCaptor.forClass(SearchCriteria.class);
        verify(client).search(captor.capture(), anyInt());
        SearchCriteria c = captor.getValue();
        assertThat(c.title()).isNull();
        assertThat(c.author()).isNull();
        assertThat(c.subject()).isNull();
        assertThat(c.language()).isNull();
    }

    @Test
    void mapsDocsToCardsWithTrimmingAndCoverUrl() {
        SearchResponse.Doc a = new SearchResponse.Doc(
                "/works/OL1W", "A Book",
                List.of("Author One", "Author Two", "Author Three", "Author Four"),
                List.of("OL1A", "OL2A", "OL3A", "OL4A"),
                42,
                List.of("s1", "s2", "s3", "s4", "s5", "s6", "s7"));
        SearchResponse.Doc b = new SearchResponse.Doc(
                "/works/OL2W", "No Cover Book",
                List.of("Lonely Author"), List.of(), null, null);
        when(client.search(any(), anyInt())).thenReturn(new SearchResponse(2, List.of(a, b)));

        BookResults results = app.searchBooks("anything", null, null, null, null, null, null, 3);

        assertThat(results.totalFound()).isEqualTo(2);
        assertThat(results.books()).hasSize(1);

        BookResults.BookCard first = results.books().get(0);
        assertThat(first.authors()).hasSize(1);
        assertThat(first.authors().get(0)).isEqualTo(new BookResults.Author("Author One", "OL1A"));
        assertThat(first.subjects()).hasSize(3);
        assertThat(first.coverUrl()).isEqualTo("https://covers.openlibrary.org/b/id/42-L.jpg");
    }
}
