package com.example.androidmessage1;

public class WallpaperItem {
    private String name;
    private int drawableResourceId;
    private String resourceName;

    public WallpaperItem(String name, int drawableResourceId, String resourceName) {
        this.name = name;
        this.drawableResourceId = drawableResourceId;
        this.resourceName = resourceName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDrawableResourceId() {
        return drawableResourceId;
    }

    public void setDrawableResourceId(int drawableResourceId) {
        this.drawableResourceId = drawableResourceId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }
}