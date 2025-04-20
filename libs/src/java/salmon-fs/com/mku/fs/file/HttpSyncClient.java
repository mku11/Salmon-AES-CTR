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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP client
 */
public class HttpSyncClient {
    private static boolean allowClearTextTraffic = false;

    /**
     * Check if clear text traffic (HTTP) is allowed, otherwise you need to use secure HTTPS protocols.
     * Clear text traffic should ONLY be used for testing purposes.
     *
     * @return True if allow clear text traffic
     */
    public static boolean getAllowClearTextTraffic() {
        return allowClearTextTraffic;
    }

    /**
     * Set to true to allow clear text traffic (HTTP), otherwise you need to use secure HTTPS protocols.
     * Clear text traffic should ONLY be used for testing purposes.
     * @param allow True to allow clear text traffic.
     */
    public static void setAllowClearTextTraffic(boolean allow) {
        allowClearTextTraffic = allow;
    }

	/**
	 * Create a new connection from a url
	 * @param urlPath The url
	 * @return The connection
	 * @throws IOException if error with IO
	 */
    public static HttpURLConnection createConnection(String urlPath) throws IOException {
        URL url = new URL(urlPath);
        if(!url.getProtocol().equals("https") && !allowClearTextTraffic)
            throw new RuntimeException("Clear text traffic should only be used for testing purpores, " +
                    "use HttpSyncClient.setAllowClearTextTraffic() to override");
		if(url.getHost() == null || url.getHost().length() == 0)
			throw new RuntimeException("Malformed URL or unknown service, check the path");
        return (HttpURLConnection) url.openConnection();
    }
}