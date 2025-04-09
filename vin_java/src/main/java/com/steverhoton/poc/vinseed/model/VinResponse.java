package com.steverhoton.poc.vinseed.model;

import java.util.List;
import java.util.Map;

/**
 * Model class for the NHTSA VIN API response
 */
public class VinResponse {
    private int Count;
    private String Message;
    private String SearchCriteria;
    private List<Map<String, String>> Results;

    public int getCount() {
        return Count;
    }

    public void setCount(int count) {
        Count = count;
    }

    public String getMessage() {
        return Message;
    }

    public void setMessage(String message) {
        Message = message;
    }

    public String getSearchCriteria() {
        return SearchCriteria;
    }

    public void setSearchCriteria(String searchCriteria) {
        SearchCriteria = searchCriteria;
    }

    public List<Map<String, String>> getResults() {
        return Results;
    }

    public void setResults(List<Map<String, String>> results) {
        Results = results;
    }
}
