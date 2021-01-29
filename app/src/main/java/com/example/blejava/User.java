package com.example.blejava;

import java.util.ArrayList;
import java.util.List;

public class User {

    String name;
    List<Integer> hrData = new ArrayList<Integer>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Integer> getHrData() {
        return hrData;
    }

    public void setHrData(List<Integer> hrData) {
        this.hrData = hrData;
    }
}
