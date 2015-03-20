/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 s1mpl3x <jan[at]over9000.eu>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package eu.over9000.skadi.lock;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleInstanceLock {

	private static final Logger LOGGER = LoggerFactory.getLogger(SingleInstanceLock.class);

	private static final int SKADI_LOCKING_PORT = 37973;
	private static final byte[] WAKEUP_SIGNATURE = "SKADI".getBytes();
	private static final Set<LockWakeupReceiver> receivers = new HashSet<>();
	private static DatagramSocket lockingSocket;

	public static boolean startSocketLock() {
		try {
			SingleInstanceLock.lockingSocket = new DatagramSocket(SingleInstanceLock.SKADI_LOCKING_PORT, InetAddress
					.getLoopbackAddress());
			final Thread wakeupReceiverThread = new Thread(() -> {
				while ((SingleInstanceLock.lockingSocket != null) && !SingleInstanceLock.lockingSocket.isClosed()) {
					final byte[] buffer = new byte[SingleInstanceLock.WAKEUP_SIGNATURE.length];

					final DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);
					try {
						SingleInstanceLock.lockingSocket.receive(incoming);

						if (Arrays.equals(SingleInstanceLock.WAKEUP_SIGNATURE, incoming.getData())) {
							SingleInstanceLock.LOGGER.info("received wakeup on locking socket");
							SingleInstanceLock.receivers.forEach(receiver -> receiver.onWakeupReceived());
						}

					} catch (final IOException e) {
						if ((SingleInstanceLock.lockingSocket == null) || SingleInstanceLock.lockingSocket.isClosed
								()) {
							return;
						}
						SingleInstanceLock.LOGGER.error("error handling locking socket", e);
					}
				}

			}, "SkadiWakeupReceiver");

			wakeupReceiverThread.start();

		} catch (final SocketException e) {
			try {
				final DatagramSocket sendWakeupSocket = new DatagramSocket(0, InetAddress.getLoopbackAddress());

				final DatagramPacket sendWakeupPacket = new DatagramPacket(SingleInstanceLock.WAKEUP_SIGNATURE,
						SingleInstanceLock.WAKEUP_SIGNATURE.length, InetAddress.getLoopbackAddress(),
						SingleInstanceLock.SKADI_LOCKING_PORT);
				sendWakeupSocket.send(sendWakeupPacket);
				sendWakeupSocket.close();

			} catch (final IOException e1) {
				SingleInstanceLock.LOGGER.error("error handling locking socket", e);
			}
		}

		return SingleInstanceLock.lockingSocket != null;
	}

	public static void addReceiver(final LockWakeupReceiver receiver) {
		SingleInstanceLock.receivers.add(receiver);
	}

	public static void stopSocketLock() {
		SingleInstanceLock.lockingSocket.close();
	}
}
