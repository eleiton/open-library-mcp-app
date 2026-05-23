package dev.eleiton.openlibrary.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResponse(int numFound, List<Doc> docs) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Doc(
            String key,
            String title,
            @JsonProperty("author_name") List<String> authorNames,
            @JsonProperty("author_key") List<String> authorKeys,
            @JsonProperty("cover_i") Integer coverId,
            @JsonProperty("subject") List<String> subjects) {
    }
}
