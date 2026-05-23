package dev.eleiton.openlibrary.client;

import dev.eleiton.openlibrary.model.SearchCriteria;
import dev.eleiton.openlibrary.model.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenLibraryClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private OpenLibraryClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenLibraryClient(builder);
    }

    @Test
    void searchByQueryHitsSearchEndpointWithFieldsAndLimit() throws Exception {
        String body = new String(new ClassPathResource("fixtures/tolkien-search.json")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        server.expect(requestToUriTemplate("https://openlibrary.org/search.json?fields={f}&limit={l}&q={q}",
                        "key,title,author_name,author_key,cover_i,subject", "9", "tolkien"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        SearchResponse response = client.search(new SearchCriteria(
                "tolkien", null, null, null, null, null, null, 3), 9);

        assertThat(response.numFound()).isEqualTo(312);
        assertThat(response.docs()).hasSize(3);
        assertThat(response.docs().get(0).title()).isEqualTo("The Lord of the Rings");
        assertThat(response.docs().get(0).authorKeys()).containsExactly("OL26320A");
        assertThat(response.docs().get(2).coverId()).isNull();
        assertThat(response.docs().get(2).subjects()).isNull();
        server.verify();
    }

    @Test
    void onlyNonBlankFieldsAreSentAsQueryParams() {
        server.expect(queryParam("subject", "Fantasy"))
                .andExpect(queryParam("author_key", "OL26320A"))
                .andExpect(queryParam("limit", "15"))
                .andRespond(withSuccess("{\"numFound\":0,\"docs\":[]}", MediaType.APPLICATION_JSON));

        client.search(new SearchCriteria(
                null, "   ", null, "OL26320A", "Fantasy", "", null, 5), 15);

        server.verify();
    }
}
