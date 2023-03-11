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
using System.Collections.Generic;
using System;
using System.IO;
using System.Text;
using System.Xml;

namespace Salmon.FS
{

    public class SalmonSequenceConfig
    {
        public static Sequence GetSequence(Dictionary<string, Sequence> configs, string driveID)
        {
            Sequence sequence = null;
            foreach (Sequence seq in configs.Values)
            {
                if (driveID.ToUpper().Equals(seq.driveID.ToUpper()))
                {
                    // there should be only one sequence available
                    if (seq.status == Status.Active || seq.status == Status.New)
                    {
                        if (sequence != null)
                            throw new Exception("Corrupt sequence config");
                        sequence = seq;
                    }
                }
            }
            return sequence;
        }

        public enum Status
        {
            New, Active, Revoked
        }

        public class Sequence
        {
            internal string driveID;
            internal string authID;
            internal byte[] nonce;
            internal byte[] maxNonce;
            internal Status status;

            public Sequence(string driveID, string authID, byte[] nextNonce, byte[] maxNonce, Status status)
            {
                this.driveID = driveID;
                this.authID = authID;
                this.nonce = nextNonce;
                this.maxNonce = maxNonce;
                this.status = status;
            }
        }

    }

}