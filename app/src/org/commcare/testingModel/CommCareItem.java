package org.commcare.testingModel;

// Model class for CommCare items
public class CommCareItem implements JobListItem {
    private String title;
    private String description;

    // Constructor
    public CommCareItem(String title, String description) {
        this.title = title;
        this.description = description;
    }

    // Getters
    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}