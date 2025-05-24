package models;

import com.google.gson.annotations.SerializedName;

public class User {

    private String uuid;
    private String email;
    private String firstname;
    private String secondname;
    private String avatarUrl;
    private String createdTime;
    private String displayName;
    private String subscriptionType;
    private boolean isUsingTestDrive;
    private boolean isUserPaid;

    // Empty constructor required for Firebase
    public User() {
    }

    public User(String uuid, String email, String firstname, String secondname, String avatarUrl, String createdTime, String displayName, String subscriptionType, boolean isUsingTestDrive, boolean isUserPaid) {
        this.uuid = uuid;
        this.email = email;
        this.firstname = firstname;
        this.secondname = secondname;
        this.avatarUrl = avatarUrl;
        this.createdTime = createdTime;
        this.displayName = displayName;
        this.subscriptionType = subscriptionType;
        this.isUsingTestDrive = isUsingTestDrive;
        this.isUserPaid = isUserPaid;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getSecondname() {
        return secondname;
    }

    public void setSecondname(String secondname) {
        this.secondname = secondname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(String subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public boolean isUsingTestDrive() {
        return isUsingTestDrive;
    }

    public void setUsingTestDrive(boolean usingTestDrive) {
        isUsingTestDrive = usingTestDrive;
    }

    public boolean isUserPaid() {
        return isUserPaid;
    }

    public void setUserPaid(boolean userPaid) {
        isUserPaid = userPaid;
    }
}
