package com.xxhy.simpleai.data.model;

/**
 * 会话实体。
 */
public class Session {

    private long id;
    private String title;
    private long apiConfigId;
    private String model;
    private long createdAt;
    private long updatedAt;

    public Session() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getApiConfigId() {
        return apiConfigId;
    }

    public void setApiConfigId(long apiConfigId) {
        this.apiConfigId = apiConfigId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
