package com.mku11.salmonfs;
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

import java.util.HashMap;

public class SalmonSequenceConfig {
    public static Sequence getSequence(HashMap<String, Sequence> configs, String driveID) throws Exception {
        Sequence sequence = null;
        for (Sequence seq : configs.values()) {
            if (driveID.toUpperCase().equals(seq.driveID.toUpperCase())) {
                // there should be only one sequence available
                if (seq.status == Status.Active || seq.status == Status.New) {
                    if (sequence != null)
                        throw new Exception("Corrupt sequence config");
                    sequence = seq;
                }
            }
        }
        return sequence;
    }

    public static enum Status {
        New, Active, Revoked
    }

    public static class Sequence {
        public String driveID;
        public String authID;
        public byte[] nonce;
        public byte[] maxNonce;
        public Status status;

        public Sequence(String driveID, String authID, byte[] nextNonce, byte[] maxNonce, Status status) {
            this.driveID = driveID;
            this.authID = authID;
            this.nonce = nextNonce;
            this.maxNonce = maxNonce;
            this.status = status;
        }
    }
}
