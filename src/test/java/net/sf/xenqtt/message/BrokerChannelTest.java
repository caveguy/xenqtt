package net.sf.xenqtt.message;

import static org.junit.Assert.*;

import java.nio.channels.SocketChannel;

import org.junit.Test;

public class BrokerChannelTest extends MqttChannelTestBase<MqttChannelTestBase<?, ?>.TestChannel, BrokerChannel> {

	@Override
	TestChannel newClientChannel() throws Exception {
		return new TestChannel("localhost", port, clientHandler, selector, 10000);
	}

	@Override
	BrokerChannel newBrokerChannel(SocketChannel brokerSocketChannel) throws Exception {
		return new BrokerChannel(brokerSocketChannel, brokerHandler, selector, 10000);
	}

	@Test
	public void testPingResponse() throws Exception {

		establishConnection();

		clientChannel.send(now, new PingReqMessage());

		readWrite(1, 0);

		assertEquals(1, clientHandler.messagesReceived.size());
		assertEquals(MessageType.PINGRESP, clientHandler.messagesReceived.get(0).getMessageType());
	}

	@Test
	public void testKeepAlive_NotYetConnected() throws Exception {

		establishConnection();

		assertEquals(Long.MAX_VALUE, brokerChannel.keepAlive(now, 0));

		assertTrue(brokerChannel.isOpen());
	}

	@Test
	public void testKeepAlive_Disconnected() throws Exception {

		establishConnection();

		brokerChannel.connected(1000);
		brokerChannel.disconnected();
		assertEquals(Long.MAX_VALUE, brokerChannel.keepAlive(now, 0));

		assertTrue(brokerChannel.isOpen());
	}

	@Test
	public void testKeepAlive_Connected_TimeNotExpired() throws Exception {

		establishConnection();

		brokerChannel.connected(1000);
		assertEquals(1400, brokerChannel.keepAlive(now, now - 100));

		assertTrue(brokerChannel.isOpen());
	}

	@Test
	public void testKeepAlive_Connected_TimeExpired() throws Exception {

		establishConnection();

		brokerChannel.connected(1000);
		assertEquals(-1, brokerChannel.keepAlive(now, now - 1500));

		assertFalse(brokerChannel.isOpen());
	}
}