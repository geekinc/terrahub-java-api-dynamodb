package com.serverless.qldb;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.serverless.ApiGatewayResponse;
import com.serverless.Response;
import com.serverless.dal.Constants;
import com.serverless.dal.DeleteLedger;
import com.serverless.dal.Product;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.Map;

public class DeleteLedgerHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private final Logger logger = Logger.getLogger(this.getClass());

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {

		// get the 'pathParameters' from input
		Map<String,String> pathParameters =  (Map<String,String>)input.get("pathParameters");
		String ledger_name = pathParameters.get("name");

		try {
		DeleteLedger.setDeletionProtection(ledger_name, false);
		DeleteLedger.delete(ledger_name);
		DeleteLedger.waitForDeleted(ledger_name);

        // send the response back
		return ApiGatewayResponse.builder()
					.setStatusCode(204)
					.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & Serverless"))
					.build();
    } catch (Exception ex) {
        logger.error("Error in deleting product: " + ex);

        // send the error response back
  			Response responseBody = new Response("Error in deleting ledger " + ledger_name + ": ", input);
  			return ApiGatewayResponse.builder()
  					.setStatusCode(500)
  					.setObjectBody(responseBody)
  					.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & Serverless"))
  					.build();
    }
	}
}
