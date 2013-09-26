/**
    Copyright 2013 James McClure

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package net.sf.xenqtt.integration;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.net.ConnectException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Arrays;
import java.util.List;

import net.sf.xenqtt.MqttCommandCancelledException;
import net.sf.xenqtt.MqttException;
import net.sf.xenqtt.MqttTimeoutException;
import net.sf.xenqtt.client.MqttClient;
import net.sf.xenqtt.client.MqttClientListener;
import net.sf.xenqtt.client.PublishMessage;
import net.sf.xenqtt.client.ReconnectionStrategy;
import net.sf.xenqtt.client.Subscription;
import net.sf.xenqtt.client.SynchronousMqttClient;
import net.sf.xenqtt.message.ConnectMessage;
import net.sf.xenqtt.message.ConnectReturnCode;
import net.sf.xenqtt.message.PubMessage;
import net.sf.xenqtt.message.QoS;
import net.sf.xenqtt.mockbroker.Client;
import net.sf.xenqtt.mockbroker.MockBroker;
import net.sf.xenqtt.mockbroker.MockBrokerHandler;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class SynchronousMqttClientIT {

	String badCredentialsUri = "tcp://q.m2m.io:1883";
	String validBrokerUri = "tcp://test.mosquitto.org:1883";

	@Mock MockBrokerHandler mockHandler;
	@Mock MqttClientListener listener;
	@Mock ReconnectionStrategy reconnectionStrategy;
	@Captor ArgumentCaptor<PublishMessage> messageCaptor;;

	MockBroker mockBroker;
	SynchronousMqttClient client;
	SynchronousMqttClient client2;

	@Before
	public void before() {
		MockitoAnnotations.initMocks(this);
	}

	@After
	public void after() {

		if (client != null) {
			client.shutdown();
		}
		if (client2 != null) {
			client2.shutdown();
		}
		if (mockBroker != null) {
			mockBroker.shutdown(5000);
		}
	}

	@Test
	public void testConstructor_InvalidScheme() throws Exception {

		try {
			client = new SynchronousMqttClient("ftp://foo:1883", listener, reconnectionStrategy, 5, 0, 5, 10);
			fail("expected exception");
		} catch (MqttException e) {
			assertEquals("Invalid broker URI (scheme must be 'tcp'): ftp://foo:1883", e.getMessage());
		}

		verifyZeroInteractions(listener, reconnectionStrategy);
	}

	@Test
	public void testConstructor_InvalidHost() throws Exception {

		Throwable thrown = null;
		try {
			client = new SynchronousMqttClient("tcp://foo:1883", listener, reconnectionStrategy, 5, 0, 5, 10);
			fail("expected exception");
		} catch (MqttException e) {
			thrown = e.getCause();
			assertEquals(UnresolvedAddressException.class, thrown.getClass());
		}

		verify(listener, timeout(5000)).disconnected(any(SynchronousMqttClient.class), same(thrown), eq(false));

		verifyNoMoreInteractions(listener);
		verifyZeroInteractions(reconnectionStrategy);
	}

	// This test can take over a minute to run so ignore it by default
	@Ignore
	@Test
	public void testConstructor_InvalidPort() throws Exception {

		try {
			client = new SynchronousMqttClient("tcp://test.mosquitto.org:1234", listener, reconnectionStrategy, 5, 0, 5, 1000);
			fail("expected exception");
		} catch (MqttCommandCancelledException e) {
			verifyZeroInteractions(listener, reconnectionStrategy);

			verify(listener, timeout(1000)).disconnected(any(MqttClient.class), any(ConnectException.class), eq(false));
			verifyNoMoreInteractions(listener);
			verifyZeroInteractions(reconnectionStrategy);
		}
	}

	@Test
	public void testConstructorTimesOut() throws Exception {

		try {
			client = new SynchronousMqttClient("tcp://test.mosquitto.org:1234", listener, reconnectionStrategy, 5, 0, 5, 1);
			fail("expected exception");
		} catch (MqttTimeoutException e) {
			verifyZeroInteractions(listener, reconnectionStrategy);
		}
	}

	@Test
	public void testConnectDisconnect_NoCredentialsNoWill() throws Exception {

		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client.connect("testclient1", true, 90));
		verify(reconnectionStrategy, timeout(5000)).connectionEstablished();

		client.disconnect();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));

		verifyNoMoreInteractions(listener, reconnectionStrategy);
	}

	@Test
	public void testConnect_Credentials_BadCredentials() throws Exception {

		client = new SynchronousMqttClient(badCredentialsUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.BAD_CREDENTIALS, client.connect("testclient2", true, 90, "not_a_user", "not_a_password"));

		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));

		verifyNoMoreInteractions(listener);
		verifyZeroInteractions(reconnectionStrategy);
	}

	@Test
	public void testConnect_Credentials_Accepted() throws Exception {

		mockBroker = new MockBroker(null, 15, 0, true);
		mockBroker.addCredentials("user1", "password1");
		mockBroker.init();
		validBrokerUri = "tcp://localhost:" + mockBroker.getPort();

		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client.connect("testclient2", true, 90, "user1", "password1"));

		client.disconnect();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));
	}

	@Test
	public void testConnect_Will_NoRetain_Subscribed() throws Exception {

		// connect and subscribe a client to get the will message
		MqttClientListener listener2 = mock(MqttClientListener.class);
		client2 = new SynchronousMqttClient(validBrokerUri, listener2, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client2.connect("testclient3", true, 90));
		assertNotNull(client2.subscribe(new Subscription[] { new Subscription("my/will/topic1", QoS.AT_LEAST_ONCE) }));

		// connect and close a client to generate the will message
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client.connect("testclient4", true, 90, "my/will/topic1", "it died dude", QoS.AT_LEAST_ONCE, false));
		client.close();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));

		// verify the will message
		verify(listener2, timeout(5000)).publishReceived(same(client2), messageCaptor.capture());
		PublishMessage message = messageCaptor.getValue();
		message.ack();
		assertEquals("my/will/topic1", message.getTopic());
		assertEquals("it died dude", message.getPayloadString());
		assertEquals(QoS.AT_LEAST_ONCE, message.getQoS());
		assertFalse(message.isDuplicate());
		assertFalse(message.isRetain());

		client2.disconnect();
		verify(listener2, timeout(5000)).disconnected(same(client2), any(Throwable.class), eq(false));

		verifyNoMoreInteractions(listener, listener2);
	}

	@Test
	public void testConnect_Will_NoRetain_NotSubscribed() throws Exception {

		// connect and close a client to generate the will message
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client.connect("testclient5", true, 90, "my/will/topic2", "it died dude", QoS.AT_LEAST_ONCE, false));
		client.close();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));

		// connect and subscribe a client to get the will message
		MqttClientListener listener2 = mock(MqttClientListener.class);
		client2 = new SynchronousMqttClient(validBrokerUri, listener2, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client2.connect("testclient6", true, 90));
		assertNotNull(client2.subscribe(new Subscription[] { new Subscription("my/will/topic2", QoS.AT_LEAST_ONCE) }));
		// verify no will message
		Thread.sleep(1000);
		verify(listener2, never()).publishReceived(same(client2), any(PublishMessage.class));
		client2.disconnect();
		verify(listener2, timeout(5000)).disconnected(same(client2), any(Throwable.class), eq(false));

		verifyNoMoreInteractions(listener, listener2);
	}

	@Test
	public void testConnect_Will_Retain_NotSubscribed() throws Exception {

		// connect and close a client to generate the will message
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client.connect("testclient7", true, 90, "my/will/topic3", "it died dude", QoS.AT_LEAST_ONCE, true));

		client.close();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));

		// connect and subscribe a client to get the will message
		MqttClientListener listener2 = mock(MqttClientListener.class);
		client2 = new SynchronousMqttClient(validBrokerUri, listener2, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client2.connect("testclient8", true, 90));
		assertNotNull(client2.subscribe(new Subscription[] { new Subscription("my/will/topic3", QoS.AT_LEAST_ONCE) }));

		// verify the will message
		verify(listener2, timeout(5000)).publishReceived(same(client2), messageCaptor.capture());
		PublishMessage message = messageCaptor.getValue();
		message.ack();
		assertEquals("my/will/topic3", message.getTopic());
		assertEquals("it died dude", message.getPayloadString());
		assertEquals(QoS.AT_LEAST_ONCE, message.getQoS());
		assertFalse(message.isDuplicate());
		assertTrue(message.isRetain());

		// remove the message
		client2.publish(new PublishMessage("my/will/topic3", QoS.AT_LEAST_ONCE, new byte[0], true));
		verify(listener2, timeout(5000)).publishReceived(same(client2), isA(PublishMessage.class));

		client2.disconnect();
		verify(listener2, timeout(5000)).disconnected(same(client2), any(Throwable.class), eq(false));

		verifyNoMoreInteractions(listener, listener2);
	}

	@Test
	public void testConnect_Will_Retain_Subscribed() throws Exception {

		// connect and close a client to generate the will message
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client.connect("testclient10", true, 90, "my/will/topic4", "it died dude", QoS.AT_LEAST_ONCE, true));
		client.close();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));

		// connect and subscribe a client to get the will message
		MqttClientListener listener2 = mock(MqttClientListener.class);
		client2 = new SynchronousMqttClient(validBrokerUri, listener2, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client2.connect("testclient9", true, 90));
		assertNotNull(client2.subscribe(new Subscription[] { new Subscription("my/will/topic4", QoS.AT_LEAST_ONCE) }));

		// verify the will message
		verify(listener2, timeout(5000)).publishReceived(same(client2), messageCaptor.capture());
		PublishMessage message = messageCaptor.getValue();
		message.ack();
		assertEquals("my/will/topic4", message.getTopic());
		assertEquals("it died dude", message.getPayloadString());
		assertEquals(QoS.AT_LEAST_ONCE, message.getQoS());
		assertFalse(message.isDuplicate());
		assertTrue(message.isRetain());

		// remove the message
		client2.publish(new PublishMessage("my/will/topic4", QoS.AT_LEAST_ONCE, new byte[0], true));
		verify(listener2, timeout(5000)).publishReceived(same(client2), isA(PublishMessage.class));

		client2.disconnect();
		verify(listener2, timeout(5000)).disconnected(same(client2), any(Throwable.class), eq(false));

		verifyNoMoreInteractions(listener, listener2);
	}

	@Test
	public void testConnect_CredentialsAndWill_Accepted() throws Exception {

		mockBroker = new MockBroker(null, 15, 0, true);
		mockBroker.addCredentials("user1", "password1");
		mockBroker.init();
		validBrokerUri = "tcp://localhost:" + mockBroker.getPort();

		// connect and subscribe a client to get the will message
		MqttClientListener listener2 = mock(MqttClientListener.class);
		client2 = new SynchronousMqttClient(validBrokerUri, listener2, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client2.connect("testclient3", true, 90));
		client2.subscribe(new Subscription[] { new Subscription("my/will/topic1", QoS.AT_LEAST_ONCE) });

		// connect and close a client to generate the will message
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED,
				client.connect("testclient4", true, 90, "user1", "password1", "my/will/topic1", "it died dude", QoS.AT_LEAST_ONCE, false));
		client.close();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));

		// verify the will message
		verify(listener2, timeout(5000)).publishReceived(same(client2), messageCaptor.capture());
		PublishMessage message = messageCaptor.getValue();
		message.ack();
		assertEquals("my/will/topic1", message.getTopic());
		assertEquals("it died dude", message.getPayloadString());
		assertEquals(QoS.AT_LEAST_ONCE, message.getQoS());
		assertFalse(message.isDuplicate());
		assertFalse(message.isRetain());

		client2.disconnect();
		verify(listener2, timeout(5000)).disconnected(same(client2), any(Throwable.class), eq(false));

		verifyNoMoreInteractions(listener, listener2);
	}

	@Test
	public void testSubscribeUnsubscribe_Array() throws Exception {

		// connect client
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client.connect("testclient11", true, 90));

		// test subscribing
		Subscription[] requestedSubscriptions = new Subscription[] { new Subscription("my/topic1", QoS.AT_LEAST_ONCE),
				new Subscription("my/topic2", QoS.AT_MOST_ONCE) };
		Subscription[] grantedSubscriptions = requestedSubscriptions;
		assertArrayEquals(grantedSubscriptions, client.subscribe(requestedSubscriptions));

		// test unsubscribing
		// FIXME [jim] - how to verify this?
		client.unsubscribe(new String[] { "my/topic1", "my/topic2" });

		// disconnect
		client.disconnect();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));

		verifyNoMoreInteractions(listener);
	}

	@Test
	public void testSubscribeUnsubscribe_List() throws Exception {

		// connect client
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client.connect("testclient12", true, 90));

		// test subscribing
		List<Subscription> requestedSubscriptions = Arrays.asList(new Subscription[] { new Subscription("my/topic3", QoS.AT_LEAST_ONCE),
				new Subscription("my/topic4", QoS.AT_MOST_ONCE) });
		List<Subscription> grantedSubscriptions = requestedSubscriptions;
		assertEquals(grantedSubscriptions, client.subscribe(requestedSubscriptions));

		// test unsubscribing
		// FIXME [jim] - how to verify this?
		client.unsubscribe(Arrays.asList(new String[] { "my/topic3", "my/topic4" }));

		// disconnect
		client.disconnect();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));

		verifyNoMoreInteractions(listener);
	}

	@Test
	public void testPublish_Qos1_NoRetain() throws Exception {

		// connect and subscribe a client to get the messages
		MqttClientListener listener2 = mock(MqttClientListener.class);
		client2 = new SynchronousMqttClient(validBrokerUri, listener2, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client2.connect("testclient13", true, 90));
		assertNotNull(client2.subscribe(new Subscription[] { new Subscription("my/topic5", QoS.AT_LEAST_ONCE) }));

		// connect a client and generate the messages
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client.connect("testclient14", true, 90));
		for (int i = 0; i < 10; i++) {
			client.publish(new PublishMessage("my/topic5", QoS.AT_LEAST_ONCE, "my message " + i));
		}
		client.disconnect();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));

		// verify the messages
		verify(listener2, timeout(5000).times(10)).publishReceived(same(client2), messageCaptor.capture());

		for (PublishMessage message : messageCaptor.getAllValues()) {
			message.ack();
			assertEquals("my/topic5", message.getTopic());
			assertTrue(message.getPayloadString().startsWith("my message "));
			assertEquals(QoS.AT_LEAST_ONCE, message.getQoS());
			assertFalse(message.isDuplicate());
			assertFalse(message.isRetain());
		}

		client2.disconnect();
		verify(listener2, timeout(5000)).disconnected(same(client2), any(Throwable.class), eq(false));

		verifyNoMoreInteractions(listener, listener2);
	}

	@Test
	public void testPublish_Qos1_Retain() throws Exception {

		// connect a client and generate the message
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client.connect("testclient16", true, 90));
		client.publish(new PublishMessage("my/topic6", QoS.AT_LEAST_ONCE, "my message", true));
		client.disconnect();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));

		// connect and subscribe a client to get the messages
		MqttClientListener listener2 = mock(MqttClientListener.class);
		client2 = new SynchronousMqttClient(validBrokerUri, listener2, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client2.connect("testclient15", true, 90));
		assertNotNull(client2.subscribe(new Subscription[] { new Subscription("my/topic6", QoS.AT_LEAST_ONCE) }));

		// verify the messages
		verify(listener2, timeout(5000)).publishReceived(same(client2), messageCaptor.capture());

		PublishMessage message = messageCaptor.getValue();
		message.ack();
		assertEquals("my/topic6", message.getTopic());
		assertEquals("my message", message.getPayloadString());
		assertEquals(QoS.AT_LEAST_ONCE, message.getQoS());
		assertFalse(message.isDuplicate());
		assertTrue(message.isRetain());

		// remove the message
		client2.publish(new PublishMessage("my/topic6", QoS.AT_LEAST_ONCE, new byte[0], true));
		verify(listener2, timeout(5000)).publishReceived(same(client2), isA(PublishMessage.class));

		client2.disconnect();
		verify(listener2, timeout(5000)).disconnected(same(client2), any(Throwable.class), eq(false));

		verifyNoMoreInteractions(listener, listener2);
	}

	@Test
	public void testPublish_Qos0_NoRetain() throws Exception {

		// connect and subscribe a client to get the messages
		MqttClientListener listener2 = mock(MqttClientListener.class);
		client2 = new SynchronousMqttClient(validBrokerUri, listener2, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client2.connect("testclient15", true, 90));
		assertNotNull(client2.subscribe(new Subscription[] { new Subscription("my/topic7", QoS.AT_LEAST_ONCE) }));

		// connect a client and generate the messages
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client.connect("testclient16", true, 90));
		for (int i = 0; i < 10; i++) {
			client.publish(new PublishMessage("my/topic7", QoS.AT_MOST_ONCE, "my message " + i));
		}

		// verify the messages
		verify(listener2, timeout(5000).times(10)).publishReceived(same(client2), messageCaptor.capture());

		for (PublishMessage message : messageCaptor.getAllValues()) {
			message.ack();
			assertEquals("my/topic7", message.getTopic());
			assertTrue(message.getPayloadString().startsWith("my message "));
			assertEquals(QoS.AT_MOST_ONCE, message.getQoS());
			assertFalse(message.isDuplicate());
			assertFalse(message.isRetain());
		}

		client.disconnect();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));

		client2.disconnect();
		verify(listener2, timeout(5000)).disconnected(same(client2), any(Throwable.class), eq(false));

		verifyNoMoreInteractions(listener, listener2);
	}

	// this test can take up to 30 seconds to run
	@Test
	public void testPublish_DuplicateMessageReceived() throws Exception {

		// connect and subscribe a client to get the message
		MqttClientListener listener2 = mock(MqttClientListener.class);
		client2 = new SynchronousMqttClient(validBrokerUri, listener2, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client2.connect("testclient18", true, 90));
		assertNotNull(client2.subscribe(new Subscription[] { new Subscription("my/topic7", QoS.AT_LEAST_ONCE) }));

		// connect a client and generate the message
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		assertEquals(ConnectReturnCode.ACCEPTED, client.connect("testclient17", true, 90));
		client.publish(new PublishMessage("my/topic7", QoS.AT_LEAST_ONCE, "my message"));
		client.disconnect();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));

		// verify the messages
		verify(listener2, timeout(30000).times(2)).publishReceived(same(client2), messageCaptor.capture());

		PublishMessage message = messageCaptor.getAllValues().get(0);
		assertEquals("my/topic7", message.getTopic());
		assertEquals("my message", message.getPayloadString());
		assertEquals(QoS.AT_LEAST_ONCE, message.getQoS());
		assertFalse(message.isDuplicate());
		assertFalse(message.isRetain());
		message = messageCaptor.getAllValues().get(1);
		message.ack();
		assertEquals("my/topic7", message.getTopic());
		assertEquals("my message", message.getPayloadString());
		assertEquals(QoS.AT_LEAST_ONCE, message.getQoS());
		assertTrue(message.isDuplicate());
		assertFalse(message.isRetain());

		client2.disconnect();
		verify(listener2, timeout(5000)).disconnected(same(client2), any(Throwable.class), eq(false));

		verifyNoMoreInteractions(listener, listener2);
	}

	@Test
	public void testClose() throws Exception {

		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		client.connect("testclient19", true, 90);
		client.close();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));
	}

	@Test
	public void testDisconnect() throws Exception {

		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 0, 5, 10);
		client.connect("testclient20", true, 90);
		client.disconnect();
		verify(listener, timeout(5000)).disconnected(eq(client), isNull(Throwable.class), eq(false));
	}

	@Test
	public void testConnectMessageTimesOut() throws Exception {

		mockBroker = new MockBroker(mockHandler, 15, 0, true);
		mockBroker.init();
		mockBroker.addCredentials("user1", "password1");
		validBrokerUri = "tcp://localhost:" + mockBroker.getPort();

		when(reconnectionStrategy.connectionLost(isA(MqttClient.class), isA(MqttTimeoutException.class))).thenReturn(-1L);

		when(mockHandler.connect(isA(Client.class), isA(ConnectMessage.class))).thenReturn(true);

		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 1, 5, 10);
		long start = System.currentTimeMillis();

		try {
			client.connect("testclient20", true, 90);
			fail("expected exception");
		} catch (MqttCommandCancelledException e) {
			assertEquals(MqttTimeoutException.class, e.getCause().getClass());
		}

		verify(listener, timeout(1500)).disconnected(eq(client), isA(MqttTimeoutException.class), eq(false));
		assertTrue(System.currentTimeMillis() - start > 500);

		verifyNoMoreInteractions(listener);
	}

	@Test
	public void testConnectionLost_FirstReconnectSucceeds() throws Exception {

		// close the broker end of the channel when it receives a pub message
		doAnswer(new Answer<Boolean>() {

			@Override
			public Boolean answer(InvocationOnMock invocation) throws Throwable {

				Client client = (Client) invocation.getArguments()[0];
				client.close();
				return true;
			}
		}).doReturn(false).when(mockHandler).publish(isA(Client.class), isA(PubMessage.class));

		// configure reconnection strategy to try to reconnect
		when(reconnectionStrategy.connectionLost(isA(MqttClient.class), isNull(Throwable.class))).thenReturn(1000L);

		// create the broker
		mockBroker = new MockBroker(mockHandler, 15, 0, true);
		mockBroker.init();
		validBrokerUri = "tcp://localhost:" + mockBroker.getPort();

		// connect to the broker and send the pub message which will cause the channel to close
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 5, 5, 10);
		client.connect("testclient20", true, 90);
		verify(reconnectionStrategy, timeout(5000)).connectionEstablished();
		PublishMessage pubMessage = new PublishMessage("foo", QoS.AT_LEAST_ONCE, "abc");
		long start = System.currentTimeMillis();
		client.publish(pubMessage);

		// verify connection was lost
		verify(reconnectionStrategy).connectionLost(isA(MqttClient.class), isNull(Throwable.class));
		verify(listener).disconnected(client, null, true);

		// verify reconnect in about 1 second
		verify(reconnectionStrategy, times(2)).connectionEstablished();
		long elapsed = System.currentTimeMillis() - start;
		assertTrue(elapsed > 500);
		assertTrue(elapsed < 1500);

		// disconnect the client
		client.disconnect();
		verify(listener, timeout(5000)).disconnected(client, null, false);
	}

	@Test
	public void testConnectionLost_NotFirstReconnectSucceeds() throws Exception {

		Answer<Boolean> answer = new Answer<Boolean>() {

			@Override
			public Boolean answer(InvocationOnMock invocation) throws Throwable {

				Client client = (Client) invocation.getArguments()[0];
				client.close();
				return true;
			}
		};
		// close the broker end of the channel when it receives a pub message
		doAnswer(answer).doAnswer(answer).doReturn(false).when(mockHandler).publish(isA(Client.class), isA(PubMessage.class));

		// configure reconnection strategy to try to reconnect
		when(reconnectionStrategy.connectionLost(isA(MqttClient.class), isNull(Throwable.class))).thenReturn(1000L);

		// create the broker
		mockBroker = new MockBroker(mockHandler, 15, 0, true);
		mockBroker.init();
		validBrokerUri = "tcp://localhost:" + mockBroker.getPort();

		// connect to the broker and send the pub message which will cause the channel to close
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 5, 5, 10);
		client.connect("testclient20", true, 90);
		verify(reconnectionStrategy, timeout(5000)).connectionEstablished();
		PublishMessage pubMessage = new PublishMessage("foo", QoS.AT_LEAST_ONCE, "abc");
		client.publish(pubMessage);

		// verify 3 connects and 2 disconnects
		verify(reconnectionStrategy, times(3)).connectionEstablished();
		verify(reconnectionStrategy, times(2)).connectionLost(isA(MqttClient.class), isNull(Throwable.class));
		verify(listener, times(2)).disconnected(client, null, true);

		// disconnect the client
		client.disconnect();
		verify(listener, timeout(5000)).disconnected(client, null, false);
	}

	@Test
	public void testConnectionLost_AllReconnectsFail() throws Exception {

		// close the broker end of the channel when it receives a pub message
		doAnswer(new Answer<Boolean>() {

			@Override
			public Boolean answer(InvocationOnMock invocation) throws Throwable {

				Client client = (Client) invocation.getArguments()[0];
				client.close();
				return true;
			}
		}).when(mockHandler).publish(isA(Client.class), isA(PubMessage.class));

		// configure reconnection strategy to try to reconnect
		when(reconnectionStrategy.connectionLost(isA(MqttClient.class), isNull(Throwable.class))).thenReturn(1000L, 1000L, 1000L, 0L);

		// create the broker
		mockBroker = new MockBroker(mockHandler, 15, 0, true);
		mockBroker.init();
		validBrokerUri = "tcp://localhost:" + mockBroker.getPort();

		// connect to the broker and send the pub message which will cause the channel to close
		client = new SynchronousMqttClient(validBrokerUri, listener, reconnectionStrategy, 5, 5, 5, 10);
		client.connect("testclient20", true, 90);
		verify(reconnectionStrategy, timeout(5000)).connectionEstablished();
		PublishMessage pubMessage = new PublishMessage("foo", QoS.AT_LEAST_ONCE, "abc");
		try {
			client.publish(pubMessage);
			fail("expected exception");
		} catch (MqttCommandCancelledException e) {

		}

		// verify 4 connects and 3 disconnects twice
		verify(reconnectionStrategy, times(4)).connectionEstablished();
		verify(reconnectionStrategy, times(4)).connectionLost(isA(MqttClient.class), isNull(Throwable.class));
		verify(listener, times(3)).disconnected(client, null, true);
		verify(listener).disconnected(client, null, false);
	}
}
