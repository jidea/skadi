/*
 * Copyright (c) 2014-2016 Jan Strauß <jan[at]over9000.eu>
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

package eu.over9000.skadi.handler;

import eu.over9000.skadi.model.Channel;
import eu.over9000.skadi.model.ChannelStore;
import eu.over9000.skadi.model.StateContainer;
import eu.over9000.skadi.model.StreamQuality;
import eu.over9000.skadi.ui.StatusBarWrapper;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The handler for the livestreamer process.
 */
public class StreamHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(StreamHandler.class);
	private final Map<Channel, StreamProcessHandler> handlers = new HashMap<>();
	private final StatusBarWrapper statusBarWrapper;
	private final StateContainer state;

	public StreamHandler(final StatusBarWrapper statusBarWrapper, final ChannelStore channelStore, final StateContainer state) {
		this.statusBarWrapper = statusBarWrapper;
		this.state = state;

		channelStore.getChannels().addListener((final ListChangeListener.Change<? extends Channel> c) -> {
			while (c.next()) {
				if (c.wasRemoved()) {
					c.getRemoved().stream().filter(handlers::containsKey).forEach(channel -> {
						final StreamProcessHandler sph = handlers.remove(channel);
						sph.closeStream();
					});
				}
			}
		});
	}

	public void openStream(final Channel channel, final StreamQuality quality) {
		if (handlers.containsKey(channel)) {
			statusBarWrapper.updateStatusText("channel " + channel.getName() + " is already open");
			return;
		}

		try {
			statusBarWrapper.updateStatusText("opening channel " + channel.getName() + " (" + quality.getQuality() + ") ...");
			final StreamProcessHandler cph = new StreamProcessHandler(channel, quality);
			handlers.put(channel, cph);
		} catch (final IOException e) {
			LOGGER.error("exception opening stream", e);
			statusBarWrapper.updateStatusText("failed to open stream: " + e.getMessage());
		}
	}

	private void updateUIStatus(final String status) {
		Platform.runLater(() -> statusBarWrapper.updateStatusText(status));
	}

	private class StreamProcessHandler implements Runnable {
		private final Process process;
		private final Channel channel;
		private final Thread thread;

		private StreamProcessHandler(final Channel forChannel, final StreamQuality quality) throws IOException {
			thread = new Thread(this);
			channel = forChannel;
			thread.setName("StreamHandler Thread for " + channel.getName());

			final List<String> args = new LinkedList<>();

			args.add(state.getExecutableLivestreamer());
			args.addAll(state.getLivestreamerArgs());
			args.add(channel.buildURL());
			args.add(quality.getQuality());

			process = new ProcessBuilder(args).redirectErrorStream(true).start();
			thread.start();
		}

		@Override
		public void run() {
			try (final BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

				String line;
				while ((line = br.readLine()) != null) {
					LOGGER.debug("LVSTRMR/VDPLYR: " + line);
					updateUIStatus("[" + channel.getName() + "] " + line.replaceAll("\\[(.*?)\\] ", ""));
				}

				process.waitFor();
			} catch (final InterruptedException | IOException e) {
				LOGGER.error("Exception handling stream process", e);
			}

			handlers.remove(channel);
		}

		public void closeStream() {
			process.destroyForcibly();
		}
	}

}
