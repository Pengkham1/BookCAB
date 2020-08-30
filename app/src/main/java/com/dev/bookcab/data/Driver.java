package com.dev.bookcab.data;

public class Driver {

    private String name, phone, carName, carRc;

    public Driver(String name, String phone, String carName, String carRc) {
        this.name = name;
        this.phone = phone;
        this.carName = carName;
        this.carRc = carRc;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getCarName() {
        return carName;
    }

    public String getCarRc() {
        return carRc;
    }
}
