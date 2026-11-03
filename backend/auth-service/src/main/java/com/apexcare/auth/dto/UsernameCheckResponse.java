package com.apexcare.auth.dto;

import java.util.List;

public class UsernameCheckResponse {

    private boolean available;
    private String username;
    private String message;
    private List<String> suggestions;

    public UsernameCheckResponse() {}

    public UsernameCheckResponse(boolean available, String username, String message, List<String> suggestions) {
        this.available = available;
        this.username = username;
        this.message = message;
        this.suggestions = suggestions != null ? suggestions : List.of();
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }
}

