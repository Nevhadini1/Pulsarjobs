package org.example;

public class Vacancies {
    private String id;              // Унікальний ID вакансії
    private String gender;          // Гендер (чоловік/жінка)
    private String city;            // Місто
    private String fullDescription; // Повний опис вакансії
    private String shortDescription;// Короткий опис вакансії
    private boolean withAccommodation;

    // Конструктор
    public Vacancies(String id, String gender, String city, String fullDescription, String shortDescription, boolean withAccommodation) {
        this.id = id;
        this.gender = gender;
        this.city = city;
        this.fullDescription = fullDescription;
        this.shortDescription = shortDescription;
        this.withAccommodation = withAccommodation;
    }

    // Геттери і сеттери
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getFullDescription() {
        return fullDescription;
    }

    public void setFullDescription(String fullDescription) {
        this.fullDescription = fullDescription;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }
    public boolean isWithAccommodation() {
        return withAccommodation;
    }

    public void setWithAccommodation(boolean withAccommodation) {
        this.withAccommodation = withAccommodation;
    }
    public boolean isSuitableForGender(String selectedGender) {
        return gender.equalsIgnoreCase("both") || gender.equalsIgnoreCase(selectedGender);
    }

    @Override
    public String toString() {
        return "Vacancies{" +
                "id='" + id + '\'' +
                ", gender='" + gender + '\'' +
                ", city='" + city + '\'' +
                ", fullDescription='" + fullDescription + '\'' +
                ", shortDescription='" + shortDescription + '\'' +
                '}';
    }
}

