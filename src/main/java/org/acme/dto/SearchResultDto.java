package org.acme.dto;

import java.util.List;

public class SearchResultDto<T> {

    public Meta meta;
    public List<T> data;

    public static class Meta {
        public int offset;
        public int limit;
        public long count;
    }
}
