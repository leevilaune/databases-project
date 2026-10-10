package dev.onesnzeroes.vendor.endpoint.common;

public class DeleteResponse {

    private String message;

    private Integer id;

    public DeleteResponse() {
    }

    public DeleteResponse(String message, Integer id) {
        this.message = message;
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
