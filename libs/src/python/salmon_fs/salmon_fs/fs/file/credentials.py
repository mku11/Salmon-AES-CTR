#!/usr/bin/env python3
"""!@brief Web Service File implementation for Python.
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

from typeguard import typechecked

@typechecked
class Credentials:
    """!
    Credentials
    """
    def __init__(self, service_user: str, service_password: str):
        """!
        Instntiate the credentials
        @param service_user: The user name
        @param service_password: The password
        """
        self.__service_user: str = service_user
        self.__service_password: str = service_password

    def get_service_user(self) -> str:
        """!
        Get the service user name
        @returns The user name
        """
        return self.__service_user

    def get_service_password(self) -> str:
        """!
        Get the service password
        @returns The password
        """
        return self.__service_password


