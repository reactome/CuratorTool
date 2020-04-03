/*
 * Created on May 2, 2012
 *
 */
package org.gk.model;

import java.io.Serializable;

/**
 * A simple class to encode Person related information.
 * @author gwu
 *
 */
public class Person implements Serializable {
    
    private String firstName;
    private String lastName;
    private String initial;
    
    public Person() {
        
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getInitial() {
        return initial;
    }

    public void setInitial(String initial) {
        this.initial = initial;
    }
    
}
