package com.noam.photodream.cloud;

import java.io.IOException;

/** The user must connect (sign in) again – retrying will not help. */
public class NotSignedInException extends IOException {
    public NotSignedInException(String message) {
        super(message);
    }
}
