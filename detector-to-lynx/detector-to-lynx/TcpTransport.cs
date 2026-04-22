using System.Net.Sockets;
using System.Text;

namespace detector_to_lynx
{
    /// <summary>
    /// Abstraction over raw TCP communication, enabling unit testing of FinishLynxRemoteService
    /// without real network connections.
    /// </summary>
    public interface ITcpTransport
    {
        Task<string> SendAndReceiveAsync(string host, int port, string packet, int timeoutMs);
    }

    /// <summary>
    /// Production implementation that uses a real TcpClient.
    /// </summary>
    public class TcpTransport : ITcpTransport
    {
        public async Task<string> SendAndReceiveAsync(string host, int port, string packet, int timeoutMs)
        {
            using var client = new TcpClient();
            await client.ConnectAsync(host, port).ConfigureAwait(false);
            using var stream = client.GetStream();

            var request = Encoding.ASCII.GetBytes(packet);
            await stream.WriteAsync(request, 0, request.Length).ConfigureAwait(false);
            await stream.FlushAsync().ConfigureAwait(false);

            client.ReceiveTimeout = timeoutMs;

            var buffer = new byte[4096];
            var responseBuilder = new StringBuilder();

            while (true)
            {
                var bytesRead = await stream.ReadAsync(buffer, 0, buffer.Length).ConfigureAwait(false);
                if (bytesRead == 0)
                    break;

                responseBuilder.Append(Encoding.ASCII.GetString(buffer, 0, bytesRead));

                if (responseBuilder.ToString().Contains("\r\n"))
                    break;
            }

            return responseBuilder.ToString().Trim();
        }
    }
}
