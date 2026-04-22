using System.Diagnostics;
using System.Text;

namespace detector_to_lynx
{
    public enum FinishLynxReply
    {
        Ok,
        Error,
        Unknown,
        InvalidResponse,
    }

    public class FinishLynxRemoteService
    {
        private readonly string _host;
        private readonly int _port;
        private readonly ITcpTransport _transport;

        public FinishLynxRemoteService(int port = 7100, string host = "127.0.0.1", ITcpTransport? transport = null)
        {
            _host = host;
            _port = port;
            _transport = transport ?? new TcpTransport();
        }

        public static FinishLynxReply ParseReply(string response)
        {
            Debug.Print("FinishLynx response: " + response);
            if (string.IsNullOrWhiteSpace(response))
                return FinishLynxReply.InvalidResponse;

            string[] tokens = response.Split("\r\n");
            foreach (string token in tokens)
            {
                if (token.StartsWith("Reply=", StringComparison.OrdinalIgnoreCase))
                {
                    string[] responseParts = token.Split(";");
                    string value = responseParts[0].Substring(6);
                    return value switch
                    {
                        "Ok" => FinishLynxReply.Ok,
                        "Error" => FinishLynxReply.Error,
                        "Unknown" => FinishLynxReply.Unknown,
                        _ => FinishLynxReply.InvalidResponse,
                    };
                }
            }
            return FinishLynxReply.InvalidResponse;
        }

        private async Task<(FinishLynxReply, string)> SendPacketAsync(Dictionary<string, string> pairs)
        {
            if (pairs == null || !pairs.ContainsKey("Command"))
                throw new ArgumentException("A 'Command' key is required.");

            var packet = new StringBuilder();
            foreach (var pair in pairs)
            {
                packet.Append($"{pair.Key}={pair.Value};");
            }
            packet.Append("\r\n");

            try
            {
                Debug.Print("Sending: " + packet.ToString());
                var response = await _transport
                    .SendAndReceiveAsync(_host, _port, packet.ToString(), 5000)
                    .ConfigureAwait(false);
                return (ParseReply(response), response);
            }
            catch (Exception ex)
            {
                throw new InvalidOperationException("Communication error: " + ex.Message, ex);
            }
        }

        public Task<(FinishLynxReply, string)> StartCreateAsync(
            string? time = null,
            string? offset = null,
            string? key = null
        )
        {
            var dict = new Dictionary<string, string> { { "Command", "StartCreate" } };
            if (!string.IsNullOrEmpty(time))
                dict["Time"] = time;
            if (!string.IsNullOrEmpty(offset))
                dict["Offset"] = offset;
            if (!string.IsNullOrEmpty(key))
                dict["Key"] = key;

            return SendPacketAsync(dict);
        }

        public Task<(FinishLynxReply, string)> EventOpenAsync(string filename) =>
            SendPacketAsync(
                new Dictionary<string, string> { { "Command", "EventOpen" }, { "File", filename } }
            );
    }
}
