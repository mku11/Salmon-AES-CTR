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

using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Runtime.CompilerServices;
using System.Threading;
using System.Threading.Tasks;

namespace Mku.FS.File;

/// <summary>
/// A synchronous HTTP client with streamed content that can be used with files.
/// </summary>
public class HttpSyncClient : HttpClient
{
    private static readonly int DEFAULT_TIMEOUT_MS = 20000;
    private static readonly int MAX_REDIRECTS = 3;

    private HttpMessageHandler MessageHandler { get; set; }

    /// <summary>
    /// Set to true to allow clear text traffic (HTTP), otherwise you need to use secure HTTPS protocols.
    /// Clear text traffic should ONLY be used for testing purposes.
    /// </summary>
    public static bool AllowClearTextTraffic { get; set; } = false;

    /// <summary>
    /// The HttpClient to use
    /// </summary>
    public static HttpSyncClient Instance { get; set; } = new HttpSyncClient(new HttpClientHandler() { AllowAutoRedirect = false});

    /// <summary>
    /// Construct a generic client based with the specified message handler
    /// </summary>
    /// <param name="messageHandler">The message handler</param>
    public HttpSyncClient(HttpMessageHandler messageHandler) : base(messageHandler)
    {
        this.MessageHandler = messageHandler;
        this.Timeout = TimeSpan.FromMilliseconds(DEFAULT_TIMEOUT_MS);
    }

    /// <summary>
    /// Send the request message synchronously. This will also default to not read the content automatically.
    /// </summary>
    /// <param name="request">The request message</param>
    /// <returns></returns>
    public virtual new HttpResponseMessage Send(HttpRequestMessage request) =>
        Send(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken: default);


    /// <summary>
    /// Send the request message synchronously.
    /// </summary>
    /// <param name="request">The request message</param>
    /// <param name="completionOption">The completion option</param>
    /// <returns></returns>
    public virtual new HttpResponseMessage Send(HttpRequestMessage request, HttpCompletionOption completionOption) =>
        Send(request, completionOption, cancellationToken: default);


    /// <summary>
    /// Send the request message synchronously. This will also default to not read the content automatically.
    /// </summary>
    /// <param name="request">The request message</param>
    /// <param name="cancellationToken">The cancellation token</param>
    /// <returns></returns>
    public override HttpResponseMessage Send(HttpRequestMessage request, CancellationToken cancellationToken) =>
        Send(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);

    /// <summary>
    /// Send the request message synchronously.
    /// </summary>
    /// <param name="request">The request message</param>
    /// <param name="completionOption">The completion option</param>
    /// <param name="cancellationToken">The cancellation token</param>
    /// <returns></returns>
    [MethodImpl(MethodImplOptions.Synchronized)]
    public virtual new HttpResponseMessage Send(HttpRequestMessage request, HttpCompletionOption completionOption, CancellationToken cancellationToken)
    {
        Uri uri = request.RequestUri;
        HttpResponseMessage response = null;
        int count = 0;
        while (count < MAX_REDIRECTS)
        {
            if (!uri.Scheme.Equals("https") && !AllowClearTextTraffic)
                throw new Exception("Clear text traffic should only be used for testing purpores, " +
                        "use HttpSyncClient.setAllowClearTextTraffic() to override");
            if (uri.Host == null || uri.Host.Length == 0)
                throw new Exception("Malformed URL or unknown service, check the path");

            Dictionary<string, IEnumerable<string>> headers = new Dictionary<string, IEnumerable<string>>();
            foreach (var header in request.Headers)
                headers.Add(header.Key, header.Value);

            Task<HttpResponseMessage> task = Task.Run(async () => await SendAsync(request, HttpCompletionOption.ResponseHeadersRead));
            task.ConfigureAwait(false);
            task.Wait();
            response = task.Result;
            if(response.Headers.Location != null)
            {
                Uri nUri = null;
                if(response.Headers.Location.IsAbsoluteUri) {
                    nUri = response.Headers.Location;
                } else
                {
                    nUri = new Uri(request.RequestUri, response.Headers.Location);
                }
                HttpRequestMessage nRequest = new HttpRequestMessage(request.Method, nUri);
                if(request.RequestUri.Host == nUri.Host)
                {
                    foreach (var header in headers)
                        nRequest.Headers.Add(header.Key, header.Value);
                }
                count += 1;
                request.Dispose();
                response.Dispose();
                request = nRequest;
            } else
            {
                break;
            }
        }
        return response;
    }
}
