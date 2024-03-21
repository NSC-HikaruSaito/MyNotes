
enum LocationType {
    ELEMENT,
    ATTRIBUTE;

    public String location;

    public LocationType setlocation(String location) {
        this.location = location;
        return this;
    }

    public String getLocation() {
        return this.location;
    }
}