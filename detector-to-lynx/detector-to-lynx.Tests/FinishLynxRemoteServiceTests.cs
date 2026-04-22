using System.Runtime.CompilerServices;
using Moq;

namespace detector_to_lynx.Tests
{
    public class FinishLynxRemoteServiceTests
    {
        // ─── ParseReply ───────────────────────────────────────────────────────

        [Theory]
        [InlineData("Reply=Ok;\r\n", FinishLynxReply.Ok)]
        [InlineData("Reply=Error;\r\n", FinishLynxReply.Error)]
        [InlineData("Reply=Unknown;\r\n", FinishLynxReply.Unknown)]
        [InlineData("Reply=SomethingElse;\r\n", FinishLynxReply.InvalidResponse)]
        [InlineData("", FinishLynxReply.InvalidResponse)]
        [InlineData("   ", FinishLynxReply.InvalidResponse)]
        [InlineData("NoReplyField=x;\r\n", FinishLynxReply.InvalidResponse)]
        public void ParseReply_ReturnsExpectedValue(string response, FinishLynxReply expected)
        {
            var result = FinishLynxRemoteService.ParseReply(response);
            Assert.Equal(expected, result);
        }

        // ─── StartCreateAsync — packet content ───────────────────────────────

        [Fact]
        public async Task StartCreate_WithTime_SendsTimeKey()
        {
            var (transport, capturedPacket) = MakeCapturingTransport("Reply=Ok;\r\n");
            var svc = new FinishLynxRemoteService(7100, "127.0.0.1", transport.Object);

            await svc.StartCreateAsync("14:23:45.123");

            Assert.Contains("Command=StartCreate;", capturedPacket.Value);
            Assert.Contains("Time=14:23:45.123;", capturedPacket.Value);
        }

        [Fact]
        public async Task StartCreate_WithOffset_SendsOffsetKey_NotTimeKey()
        {
            var (transport, capturedPacket) = MakeCapturingTransport("Reply=Ok;\r\n");
            var svc = new FinishLynxRemoteService(7100, "127.0.0.1", transport.Object);

            await svc.StartCreateAsync(null, "600.000");

            Assert.Contains("Offset=600.000;", capturedPacket.Value);
            Assert.DoesNotContain("Time=", capturedPacket.Value);
        }

        [Fact]
        public async Task StartCreate_WithNoArgs_SendsCommandOnly()
        {
            var (transport, capturedPacket) = MakeCapturingTransport("Reply=Ok;\r\n");
            var svc = new FinishLynxRemoteService(7100, "127.0.0.1", transport.Object);

            await svc.StartCreateAsync();

            Assert.Contains("Command=StartCreate;", capturedPacket.Value);
            Assert.DoesNotContain("Time=", capturedPacket.Value);
            Assert.DoesNotContain("Offset=", capturedPacket.Value);
        }

        // ─── Reply parsing via StartCreateAsync ──────────────────────────────

        [Fact]
        public async Task StartCreate_ReturnsOk_WhenFinishLynxRespondsOk()
        {
            var (transport, _) = MakeCapturingTransport("Reply=Ok;\r\n");
            var svc = new FinishLynxRemoteService(transport: transport.Object);

            var (reply, _) = await svc.StartCreateAsync("10:00:00");

            Assert.Equal(FinishLynxReply.Ok, reply);
        }

        [Fact]
        public async Task StartCreate_ReturnsError_WhenFinishLynxRespondsError()
        {
            var (transport, _) = MakeCapturingTransport("Reply=Error;\r\n");
            var svc = new FinishLynxRemoteService(transport: transport.Object);

            var (reply, _) = await svc.StartCreateAsync("10:00:00");

            Assert.Equal(FinishLynxReply.Error, reply);
        }

        // ─── Transport failure ────────────────────────────────────────────────

        [Fact]
        public async Task StartCreate_ThrowsInvalidOperationException_OnTransportFailure()
        {
            var transport = new Mock<ITcpTransport>();
            transport
                .Setup(t => t.SendAndReceiveAsync(It.IsAny<string>(), It.IsAny<int>(), It.IsAny<string>(), It.IsAny<int>()))
                .ThrowsAsync(new System.Net.Sockets.SocketException());

            var svc = new FinishLynxRemoteService(transport: transport.Object);

            await Assert.ThrowsAsync<InvalidOperationException>(() => svc.StartCreateAsync("10:00:00"));
        }

        // ─── Helpers ─────────────────────────────────────────────────────────

        private static (Mock<ITcpTransport> transport, StrongBox<string> captured) MakeCapturingTransport(string returnValue)
        {
            var captured = new StrongBox<string>(string.Empty);
            var mock = new Mock<ITcpTransport>();
            mock.Setup(t => t.SendAndReceiveAsync(
                    It.IsAny<string>(), It.IsAny<int>(), It.IsAny<string>(), It.IsAny<int>()))
                .Callback<string, int, string, int>((_, _, packet, _) => captured.Value = packet)
                .ReturnsAsync(returnValue);
            return (mock, captured);
        }
    }
}
