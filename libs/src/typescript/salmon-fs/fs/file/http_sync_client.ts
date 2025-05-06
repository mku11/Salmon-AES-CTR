/*
MIT License

Copyright (c) 2025 Max Kas

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
 * Http client
 */
export class HttpSyncClient {
    // static getResponse(arg0: string, arg1: RequestInit): Response | PromiseLike<Response | null> | null {
    //     throw new Error("Method not implemented.");
    // }
    static #allowClearTextTraffic: boolean = false;

    /**
     * Check if clear text traffic (HTTP) is allowed, otherwise you need to use secure HTTPS protocols.
     * Clear text traffic should ONLY be used for testing purposes.
     *
     * @return True if allow clear text traffic
     */
    public static getAllowClearTextTraffic(): boolean {
        return HttpSyncClient.#allowClearTextTraffic;
    }

    /**
     * Set to true to allow clear text traffic (HTTP), otherwise you need to use secure HTTPS protocols.
     * Clear text traffic should ONLY be used for testing purposes.
     * @param allow True to allow clear text traffic.
     */
    public static setAllowClearTextTraffic(allow: boolean) {
        HttpSyncClient.#allowClearTextTraffic = allow;
    }

	/**
	 * Get a response from a url
	 * @param urlPath The url
	 * @return The response
	 * @throws Error if error with IO
	 */
    static async getResponse(urlpath: string, init?: RequestInit): Promise<Response>{
        let url: URL = new URL(urlpath);
        if(url.protocol !== "https:" && !HttpSyncClient.#allowClearTextTraffic)
            throw new Error("Clear text traffic should only be used for testing purpores, " +
                    "use HttpSyncClient.setAllowClearTextTraffic() to override");
		if(url.hostname == null || url.hostname.length == 0)
			throw new Error("Malformed URL or unknown service, check the path");
        return await fetch(url, init);
    }
}
