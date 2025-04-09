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

using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;

namespace Mku.FS.File;

/// <summary>
/// A synchronous HTTP client with streamed content that can be used with files.
/// </summary>
public class HttpSyncClient : HttpClient
{
    /// <summary>
    /// Construct a generic client
    /// </summary>
    public HttpSyncClient() : base()
    {

    }

    /// <summary>
    /// Construct a generic client based with the specified message handler
    /// </summary>
    /// <param name="messageHandler"></param>
    public HttpSyncClient(HttpMessageHandler messageHandler) : base(messageHandler)
    {

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
    public virtual new HttpResponseMessage Send(HttpRequestMessage request, HttpCompletionOption completionOption, CancellationToken cancellationToken)
    {
        var task = Task.Run(async () => await SendAsync(request, HttpCompletionOption.ResponseHeadersRead));
        task.Wait();
        HttpResponseMessage response = task.Result;
        return response;
    }
}
