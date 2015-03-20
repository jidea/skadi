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
package eu.over9000.skadi.remote;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.over9000.skadi.model.Channel;
import eu.over9000.skadi.model.StreamQuality;
import eu.over9000.skadi.util.HttpUtil;
import eu.over9000.skadi.util.M3UUtil;

/**
 * This class provides static methods that retrieve available stream qualities from the twitch API.
 *
 * @author Jan Strauß
 */
public class StreamQualityRetriever {

	private static final Logger LOGGER = LoggerFactory.getLogger(StreamQualityRetriever.class);

	private static final JsonParser parser = new JsonParser();

	private static final String API_URL = "http://api.twitch.tv/api/channels/%s/access_token";
	private static final String USHER_URL = "http://usher.twitch.tv/api/channel/hls/%s" + "" +
			".m3u8?sig=%s&token=%s&allow_source=true";

	public static List<StreamQuality> getQualities(final Channel channel) {
		for (int tryCount = 0; tryCount < 5; tryCount++) {
			try {

				final String tokenResponse = HttpUtil.getAPIResponse(String.format(StreamQualityRetriever.API_URL,
						channel.getName().toLowerCase()));

				final JsonObject parsedTokenResponse = StreamQualityRetriever.parser.parse(tokenResponse)
						.getAsJsonObject();

				final String token = parsedTokenResponse.get("token").getAsString();
				final String sig = parsedTokenResponse.get("sig").getAsString();

				final String vidURL = String.format(StreamQualityRetriever.USHER_URL, channel.getName().toLowerCase(),
						sig, URLEncoder.encode(token, "UTF-8"));

				final String vidResponse = HttpUtil.getAPIResponse(vidURL);

				return M3UUtil.parseString(vidResponse);
			} catch (final URISyntaxException | IOException e) {
				StreamQualityRetriever.LOGGER.error("failed to retrieve stream qualites for " + channel.getName() +
						"," +
						" reason: " + e.getMessage());
			}
		}
		return null;
	}

}
