package com.serverless;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.serverless.dal.Product;
import org.apache.log4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.qldbsession.AmazonQLDBSessionClientBuilder;

import software.amazon.qldb.PooledQldbDriver;
import software.amazon.qldb.QldbDriver;
import software.amazon.qldb.QldbSession;
import software.amazon.qldb.exceptions.QldbClientException;

public class GetLedgerHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private final Logger logger = Logger.getLogger(this.getClass());
	public static AWSCredentialsProvider credentialsProvider;
	public static String endpoint = null;
	public static String region = null;

	public PooledQldbDriver driver = null;

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {

		try {
			// get the 'pathParameters' from input
			Map<String,String> pathParameters =  (Map<String,String>)input.get("pathParameters");
			String ledgerName = pathParameters.get("name");

			driver = createQldbDriver(ledgerName);

			try (QldbSession qldbSession = createQldbSession()) {
//				logger.info("Listing table names ");
//				for (String tableName : qldbSession.getTableNames()) {
//					logger.info(tableName);
//				}
				return ApiGatewayResponse.builder()
						.setStatusCode(200)
						.setObjectBody( qldbSession.getTableNames() )
						.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & Serverless"))
						.build();

			} catch (QldbClientException e) {
				logger.error("Unable to create session.", e);
				return ApiGatewayResponse.builder()
						.setStatusCode(404)
						.setObjectBody("Ledger with name: '" + ledgerName + "' not found.")
						.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & Serverless"))
						.build();
			}

		} catch (Exception ex) {
			logger.error("Error in retrieving product: " + ex);

			// send the error response back
				Response responseBody = new Response("Error in retrieving ledger: ", input);
				return ApiGatewayResponse.builder()
						.setStatusCode(500)
						.setObjectBody(responseBody)
						.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & Serverless"))
						.build();
		}
	}

	/**
	 * Create a pooled driver for creating sessions.
	 *
	 * @return The pooled driver for creating sessions.
	 */
	public static PooledQldbDriver createQldbDriver(String ledgerName) {
		AmazonQLDBSessionClientBuilder builder = AmazonQLDBSessionClientBuilder.standard();
		if (null != endpoint && null != region) {
			builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region));
		}
		if (null != credentialsProvider) {
			builder.setCredentials(credentialsProvider);
		}
		return PooledQldbDriver.builder()
				.withLedger(ledgerName)
				.withRetryLimit(4)
				.withSessionClientBuilder(builder)
				.build();
	}

	/**
	 * Connect to a ledger through a {@link QldbDriver}.
	 *
	 * @return {@link QldbSession}.
	 */
	public QldbSession createQldbSession() {
		return driver.getSession();
	}
}
