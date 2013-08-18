package net.sf.xenqtt.message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Default {@link MqttChannel} implementation. This class is NOT thread safe. At construction a {@link SocketChannel} will be registered with the
 * {@link Selector} specified in the constructor. The new instance of this class will be available from {@link SelectionKey#attachment()}.
 */
public class MqttChannelImpl implements MqttChannel {

	private final MessageHandler handler;
	private final SocketChannel channel;
	private SelectionKey selectionKey;

	// reads the first byte of the fixed header
	private final ByteBuffer readHeader1 = ByteBuffer.allocate(2);

	// reads the next 3 bytes if the remaining length is > 127
	private final ByteBuffer readHeader2 = ByteBuffer.allocate(3);

	// created on the fly to read any remaining data.
	private ByteBuffer readRemaining;

	// the remaining length value for the message currently being read
	private int remainingLength;

	private final Queue<ByteBuffer> writesPending = new ArrayDeque<ByteBuffer>();

	private ByteBuffer sendBuffer;

	/**
	 * Starts an asynchronous connection to the specified host and port. When a {@link SelectionKey} for the specified selector has
	 * {@link SelectionKey#OP_CONNECT} as a ready op then {@link #finishConnect()} should be called.
	 */
	public MqttChannelImpl(String host, int port, MessageHandler handler, Selector selector) throws IOException {

		this.channel = SocketChannel.open();
		this.channel.configureBlocking(false);
		this.handler = handler;
		this.selectionKey = channel.register(selector, SelectionKey.OP_CONNECT, this);
		this.channel.connect(new InetSocketAddress(host, port));
	}

	/**
	 * Use this constructor for clients accepted from a {@link ServerSocketChannel}.
	 */
	public MqttChannelImpl(SocketChannel channel, MessageHandler handler, Selector selector) throws IOException {
		this.handler = handler;
		this.channel = channel;
		this.channel.configureBlocking(false);
		this.selectionKey = channel.register(selector, SelectionKey.OP_READ, this);
	}

	/**
	 * @see net.sf.xenqtt.message.MqttChannel#register(java.nio.channels.Selector)
	 */
	@Override
	public void register(Selector selector) throws IOException {

		int ops = selectionKey.interestOps();
		selectionKey.cancel();
		selectionKey = channel.register(selector, ops, this);
	}

	/**
	 * @see net.sf.xenqtt.message.MqttChannel#finishConnect()
	 */
	@Override
	public void finishConnect() throws IOException {

		if (channel.finishConnect()) {
			selectionKey.interestOps(SelectionKey.OP_READ);
		}
	}

	/**
	 * @see net.sf.xenqtt.message.MqttChannel#read()
	 */
	@Override
	public boolean read() throws IOException {

		if (readRemaining != null) {
			return readRemaining();
		}

		if (readHeader1.hasRemaining()) {
			int result = channel.read(readHeader1);
			if (readHeader1.hasRemaining()) {
				return result >= 0;
			}
		}

		byte firstLenByte = readHeader1.get(1);
		if (firstLenByte == 0) {
			processMessage(readHeader1);
			return true;
		}

		if ((firstLenByte & 0x80) == 0) {
			return readRemaining();
		}

		if (readHeader2.hasRemaining()) {
			int result = channel.read(readHeader2);
			if (readHeader2.hasRemaining()) {
				return result >= 0;
			}
		}

		return readRemaining();
	}

	/**
	 * @see net.sf.xenqtt.message.MqttChannel#send(net.sf.xenqtt.message.MqttMessage)
	 */
	@Override
	public void send(MqttMessage message) throws IOException {

		ByteBuffer buffer = message.buffer;

		if (sendBuffer != null) {
			writesPending.offer(buffer);
			return;
		}

		sendBuffer = buffer;
		selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		write();
	}

	/**
	 * @see net.sf.xenqtt.message.MqttChannel#write()
	 */
	@Override
	public void write() throws IOException {

		while (sendBuffer != null) {
			int bytesWritten = channel.write(sendBuffer);
			if (bytesWritten == 0 || sendBuffer.hasRemaining()) {
				return;
			}
			sendBuffer = writesPending.poll();
		}

		selectionKey.interestOps(SelectionKey.OP_READ);
	}

	/**
	 * @see net.sf.xenqtt.message.MqttChannel#close()
	 */
	@Override
	public void close() {

		try {
			selectionKey.cancel();
			channel.close();
		} catch (IOException ignore) {
		}
	}

	private void processMessage(ByteBuffer buffer) {

		buffer.flip();

		MessageType messageType = MessageType.lookup((buffer.get(0) & 0xf0) >> 4);
		switch (messageType) {
		case CONNECT:
			handler.handle(new ConnectMessage(buffer, remainingLength));
			break;
		case CONNACK:
			handler.handle(new ConnAckMessage(buffer));
			break;
		case PUBLISH:
			handler.handle(new PublishMessage(buffer, remainingLength));
			break;
		case PUBACK:
			handler.handle(new PubAckMessage(buffer));
			break;
		case PUBREC:
			handler.handle(new PubRecMessage(buffer));
			break;
		case PUBREL:
			handler.handle(new PubRelMessage(buffer));
			break;
		case PUBCOMP:
			handler.handle(new PubCompMessage(buffer));
			break;
		case SUBSCRIBE:
			handler.handle(new SubscribeMessage(buffer, remainingLength));
			break;
		case SUBACK:
			handler.handle(new SubAckMessage(buffer, remainingLength));
			break;
		case UNSUBSCRIBE:
			handler.handle(new UnsubscribeMessage(buffer, remainingLength));
			break;
		case UNSUBACK:
			handler.handle(new UnsubAckMessage(buffer));
			break;
		case PINGREQ:
			handler.handle(new PingReqMessage(buffer));
			break;
		case PINGRESP:
			handler.handle(new PingRespMessage(buffer));
			break;
		case DISCONNECT:
			handler.handle(new DisconnectMessage(buffer));
			break;
		default:
			throw new IllegalStateException("Unsupported message type: " + messageType);
		}

		readHeader1.clear();
		readHeader2.clear();
		readRemaining = null;
		remainingLength = 0;
	}

	/**
	 * Sets {@link #remainingLength}
	 * 
	 * @return The number of bytes in the remaining length field in the message
	 */
	private int calculateRemainingLength() {

		int byteCount = 0;
		byte b;
		int multiplier = 1;
		do {
			b = byteCount == 0 ? readHeader1.get(1) : readHeader2.get(byteCount - 1);
			remainingLength += (b & 0x7f) * multiplier;
			multiplier *= 0x80;
			byteCount++;
		} while ((b & 0x80) != 0);

		return byteCount;
	}

	private boolean readRemaining() throws IOException {

		if (readRemaining == null) {
			int remainingLengthSize = calculateRemainingLength();
			int headerSize = 1 + remainingLengthSize;
			readRemaining = ByteBuffer.allocate(remainingLength + headerSize);
			readHeader1.flip();
			readRemaining.put(readHeader1);

			if (readHeader2.position() > 0) {
				readHeader2.flip();
				readRemaining.put(readHeader2);
			}
		}

		int result = channel.read(readRemaining);
		if (readRemaining.hasRemaining()) {
			return result >= 0;
		}

		processMessage(readRemaining);

		return true;
	}
}