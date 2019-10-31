package com.serverless;

import com.amazon.ion.IonString;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.qldb.AmazonQLDB;
import com.amazonaws.services.qldb.model.DescribeLedgerRequest;
import com.amazonaws.services.qldb.model.DescribeLedgerResult;
import com.amazonaws.services.qldbsession.AmazonQLDBSessionClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverless.dal.Constants;
import com.serverless.dal.CreateLedger;
import org.apache.log4j.Logger;
import com.amazon.ion.IonValue;
import software.amazon.qldb.*;
import software.amazon.qldb.exceptions.QldbClientException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class QueryLedgerHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private final Logger logger = Logger.getLogger(this.getClass());
	public static AmazonQLDB client = CreateLedger.getClient();
	public static AWSCredentialsProvider credentialsProvider;
	public static String endpoint = null;
	public static String region = null;
	public PooledQldbDriver driver = null;

	/**
	 * Query the QLDB Ledger based on parameters passed in
	 *
	 * @param txn
	 *              The {@link TransactionExecutor} for lambda execute.
	 * @param paramId
	 *              The id used to query
	 * @throws IllegalStateException if failed to convert parameters into {@link IonValue}.
	 */
	public static Result executeGeneralQuery(final TransactionExecutor txn, final String paramId) {
		final String documentId = paramId;
		final String query = "SELECT * FROM Vehicle AS v\n" +
				"WHERE v.VIN = '" + paramId + "'";
		final Result result = txn.execute(query);
		return result;
	}

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {

      try {
		  // get the 'pathParameters' from input
		  Map<String,String> pathParameters =  (Map<String,String>)input.get("pathParameters");
		  String ledgerName = pathParameters.get("name");

          // get the 'body' from input
          JsonNode body = new ObjectMapper().readTree((String) input.get("body"));
          String paramID = body.get("vin").asText();

		  driver = createQldbDriver(ledgerName);

		  try (QldbSession qldbSession = createQldbSession()) {
			  List<String> qldbResultOutput = new ArrayList<>();

			  qldbSession.execute(txn -> {
				  Result result = executeGeneralQuery(txn, paramID);
				  result.iterator().forEachRemaining(row -> qldbResultOutput.add(row.toPrettyString()));
			  }, (retryAttempt) -> logger.info("Retrying due to OCC conflict..."));

			  return ApiGatewayResponse.builder()
					  .setStatusCode(200)
					  .setObjectBody( qldbResultOutput )
					  .setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & Serverless"))
					  .build();

		  } catch (QldbClientException e) {
			  logger.error("Unable to create session.", e);
			  return ApiGatewayResponse.builder()
					  .setStatusCode(404)
					  .setObjectBody("Problem Loading Results From Ledger: '" + ledgerName)
					  .setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & Serverless"))
					  .build();
		  }

      } catch (Exception ex) {
          logger.error("Error in creating ledger: " + ex);

          // send the error response back
    			Response responseBody = new Response(ex.getMessage(), input);
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
