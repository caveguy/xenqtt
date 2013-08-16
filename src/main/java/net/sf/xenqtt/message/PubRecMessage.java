package net.sf.xenqtt.message;

import java.nio.ByteBuffer;

/**
 * A PUBREC message is the response to a PUBLISH message with QoS level 2. It is the second message of the QoS level 2 protocol flow. A PUBREC message is sent
 * by the server in response to a PUBLISH message from a publishing client, or by a subscriber in response to a PUBLISH message from the server.
 * <p>
 * When it receives a PUBREC message, the recipient sends a PUBREL message to the sender with the same Message ID as the PUBREC message.
 */
public final class PubRecMessage extends MqttMessage {

	/**
	 * Used to construct a received message.
	 */
	public PubRecMessage(ByteBuffer buffer, int remainingLength) {
		super(buffer, remainingLength);
	}

	/**
	 * Used to construct a message for sending
	 */
	public PubRecMessage(int messageId) {
		super(MessageType.PUBREC, 2);
		buffer.putShort((short) messageId);
	}

	/**
	 * The Message ID for the acknowledged PUBLISH.
	 * 
	 * @see PublishMessage#getMessageId()
	 */
	public int getMessageId() {
		return buffer.getShort(2) & 0xffff;
	}

	/**
	 * Sets the message ID
	 */
	public void setMessageId(int messageId) {
		buffer.putShort(2, (short) messageId);
	}
}