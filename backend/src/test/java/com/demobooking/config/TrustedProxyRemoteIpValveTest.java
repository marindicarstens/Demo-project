package com.demobooking.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.catalina.valves.ValveBase;
import org.junit.jupiter.api.Test;

/**
 * The trusted case, without a Spring context: Tomcat's RemoteIpValve configured the way Spring
 * Boot configures it from application.yml's server.tomcat.remoteip.* defaults. Only the Nginx
 * container's static address may set the client address through X-Forwarded-For.
 */
class TrustedProxyRemoteIpValveTest {

	private static final String NGINX_ADDRESS = "172.28.0.10";

	@Test
	void forwardedFor_fromTrustedProxy_becomesTheRemoteAddress() throws Exception {
		assertThat(remoteAddressSeenBehindValve(NGINX_ADDRESS, "203.0.113.9")).isEqualTo("203.0.113.9");
	}

	@Test
	void forwardedFor_fromAnyOtherAddress_isIgnored() throws Exception {
		assertThat(remoteAddressSeenBehindValve("172.28.0.11", "203.0.113.9")).isEqualTo("172.28.0.11");
	}

	private static String remoteAddressSeenBehindValve(String peerAddress, String forwardedFor) throws Exception {
		RemoteIpValve valve = new RemoteIpValve();
		valve.setInternalProxies("172\\.28\\.0\\.10");
		valve.setRemoteIpHeader("x-forwarded-for");
		RemoteAddressCapture capture = new RemoteAddressCapture();
		valve.setNext(capture);

		org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
		coyoteRequest.getMimeHeaders().addValue("X-Forwarded-For").setString(forwardedFor);
		Request request = new Request(new Connector(), coyoteRequest);
		request.setRemoteAddr(peerAddress);
		request.setRemoteHost(peerAddress);

		valve.invoke(request, new Response(new org.apache.coyote.Response()));
		return capture.remoteAddress;
	}

	/** Records the remote address the next valve in the pipeline sees. */
	private static final class RemoteAddressCapture extends ValveBase {

		private String remoteAddress;

		@Override
		public void invoke(Request request, Response response) {
			remoteAddress = request.getRemoteAddr();
		}

	}

}
