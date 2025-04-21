#!/usr/bin/env python3
"""!@brief Http client for Python.
"""

from __future__ import annotations

__license__ = """
MIT License

Copyright (c) 2025 Max Kas

Permission is hereby granted, free of charge, to Any person obtaining a copy
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
"""

import http.client
from http.client import HTTPConnection
from urllib.parse import urlparse

from typeguard import typechecked

@typechecked
class HttpSyncClient:
    """!
    HTTP Client
    """

    __allow_clear_text_traffic: bool = False

    @staticmethod
    def get_allow_clear_text_traffic():
        """
        Check if clear text traffic (HTTP) is allowed, otherwise you need to use secure HTTPS protocols.
        Clear text traffic should ONLY be used for testing purposes.
        @returns The
        """
        return HttpSyncClient.__allow_clear_text_traffic

    @staticmethod
    def set_allow_clear_text_traffic(allow: bool):
        """
        Set to true to allow clear text traffic (HTTP), otherwise you need to use secure HTTPS protocols.
        Clear text traffic should ONLY be used for testing purposes.
        @param allow True to allow clear text traffic.
        """
        HttpSyncClient.__allow_clear_text_traffic = allow

    @staticmethod
    def create_connection(url: str) -> HTTPConnection:
        """
        Create a new connection
        @param url: The url
        @returns The connection
        @exception IOError: Thrown if there is an IO error.
        """
        conn: HTTPConnection | None = None
        host = urlparse(url).netloc
        scheme = urlparse(url).scheme

        if scheme != "https" and not HttpSyncClient.__allow_clear_text_traffic:
            raise IOError("Clear text traffic should only be used for testing purpores, " +
                         "use HttpSyncClient.setAllowClearTextTraffic() to override")
        if host is None or len(host) == 0:
            raise IOError("Malformed URL or unknown service, check the path")

        if scheme == "http":
            conn = http.client.HTTPConnection(host)
        elif scheme == "https":
            conn = http.client.HTTPSConnection(host)
        return conn