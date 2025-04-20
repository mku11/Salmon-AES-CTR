package com.mku.fs.file;
/*
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

/**
 * Credentials.
 */
public class Credentials {
    private final String serviceUser;

    /**
     * Get the user name.
     *
     * @return The user name.
     */
    public String getServiceUser() {
        return serviceUser;
    }

    /**
     * Get the user password.
     *
     * @return The user password.
     */
    public String getServicePassword() {
        return servicePassword;
    }

    private final String servicePassword;

    /**
     * Instantiate a Credentials object.
     *
     * @param serviceUser     The user name.
     * @param servicePassword The user password.
     */
    public Credentials(String serviceUser, String servicePassword) {
        this.serviceUser = serviceUser;
        this.servicePassword = servicePassword;
    }
}