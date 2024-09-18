package org.commcare.testingModel;

public class ConnectHomeItem implements JobListItem {
    private String name;
    private String address;

    // Constructor
    public ConnectHomeItem(String name, String address) {
        this.name = name;
        this.address = address;
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }
}